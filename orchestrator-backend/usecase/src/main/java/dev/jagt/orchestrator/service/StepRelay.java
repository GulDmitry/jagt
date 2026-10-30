package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * A step's facts are jagt's and come first; what the session is asked to do with them is the brief's. Every relay
 * opens the session's turn. Each answers false where no case sends the step at the task's status, or where the
 * session still holds the turn this step opened with the same facts. The turn is recorded before the session is
 * told, and put back where telling it fails, which stops the task for the human.
 * A step the brief leaves empty sends its facts alone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StepRelay {

    private final SessionBrief brief;
    private final AgentSessions sessions;
    private final CaseDoor door;
    private final TaskTurns turns;

    /**
     * The write that opens the turn together with what else must land with it, made while the task still stands as
     * {@code read}; the session is told after the write, outside any lock. {@code undo} puts the task back, given the
     * turn the write opened, and answers false where the task moved on since.
     */
    public record Claim(TaskState read, BooleanSupplier standing,
                        Function<UnaryOperator<TaskState>, FlowReports.Claimed> write, Predicate<Turn> undo) {
    }

    /** Where the task stands now, for a caller with no read of its own to decide on. */
    public Standing standing(String taskId) {
        return turns.standing(taskId).orElse(Standing.ABSENT);
    }

    public boolean relay(String taskId, Step step, String facts, Standing read) {
        requireStanding(taskId, step.id(), read);
        return opened(taskId, step, facts, false, null, told -> sessions.writeTaskContext(taskId, told));
    }

    public boolean relay(String taskId, Step step, String facts, Claim claim) {
        return opened(taskId, step, facts, false, claim, told -> sessions.writeTaskContext(taskId, told));
    }

    /** A round sent back to work, false too where the rounds of this step running reached their ceiling. */
    public boolean relayRound(String taskId, Step step, String facts, Standing read) {
        requireStanding(taskId, step.id(), read);
        return opened(taskId, step, facts, true, null, told -> sessions.writeTaskContext(taskId, told));
    }

    public boolean relayRound(String taskId, Step step, String facts, Claim claim) {
        return opened(taskId, step, facts, true, claim, told -> sessions.writeTaskContext(taskId, told));
    }

    /** Written whatever the session already holds, as a hand-off that must land again. */
    public boolean write(String taskId, Step step, String facts, Standing read) {
        return sent(taskId, step, facts, read, told -> sessions.writeTaskContext(taskId, told));
    }

    /** Below an instruction still pending, which stands. */
    public boolean relayUnder(String taskId, Step step, String facts, boolean fresh, Standing read) {
        requireStanding(taskId, step.id(), read);
        return opened(taskId, step, facts, false, null, told -> sessions.relayUnder(taskId, told, fresh));
    }

    public boolean relayUnder(String taskId, Step step, String facts, boolean fresh, Claim claim) {
        return opened(taskId, step, facts, false, claim, told -> sessions.relayUnder(taskId, told, fresh));
    }

    /** Typed into the session's window, which takes one line. */
    public boolean say(String taskId, Step step, String facts, Standing read) {
        return sent(taskId, step, facts, read,
                told -> sessions.say(taskId, told.replaceAll("\\s*\\n\\s*", " ")));
    }

    /** A round of text already holding its steps' sections, sent afresh; every step has passed the door at {@code checked}. */
    public boolean relayComposed(String taskId, List<Step> steps, String text, @Nullable TaskStatus checked,
                                 Standing read) {
        String step = Step.joined(steps);
        requireStanding(taskId, step, read);
        String digest = Turn.digestOf(text);
        if (turns.of(taskId).holds(step, digest) || !door.roundDue(taskId, step)) {
            return false;
        }
        recorded(taskId, step, digest, checked, () -> sessions.relayFresh(taskId, text));
        return true;
    }

    private boolean sent(String taskId, Step step, String facts, Standing read, Consumer<String> send) {
        requireStanding(taskId, step.id(), read);
        CaseDoor.Admission admission = door.opens(taskId, step);
        if (!admission.admitted()) {
            return false;
        }
        recorded(taskId, step.id(), Turn.digestOf(facts), admission.status(), () -> send.accept(told(step, facts)));
        return true;
    }

    private boolean opened(String taskId, Step step, String facts, boolean round, @Nullable Claim claim,
                           Consumer<String> send) {
        if (claim != null) {
            requireStanding(taskId, step, claim);
        }
        CaseDoor.Admission admission = door.opens(taskId, step);
        if (!admission.admitted()) {
            return false;
        }
        String digest = Turn.digestOf(facts);
        if (turns.of(taskId).holds(step.id(), digest) || round && !door.roundDue(taskId, step.id())) {
            return false;
        }
        if (claim == null) {
            recorded(taskId, step.id(), digest, admission.status(), () -> send.accept(told(step, facts)));
            return true;
        }
        AtomicReference<Turn> opened = new AtomicReference<>();
        UnaryOperator<TaskState> opening = turns.opening(step.id(), digest);
        FlowReports.Claimed claimed = claim.write().apply(task -> {
            TaskState written = opening.apply(task);
            opened.set(written.turnOrNone());
            return written;
        });
        if (claimed == FlowReports.Claimed.MOVED_ON) {
            throw dropped(taskId, step.id(), claim);
        }
        if (claimed == FlowReports.Claimed.GONE) {
            return false;
        }
        turns.announced(taskId, step.id(), admission.status());
        try {
            send.accept(told(step, facts));
        } catch (RuntimeException failure) {
            undone(taskId, step.id(), claim, opened.get(), failure);
            throw failure;
        }
        return true;
    }

    private void recorded(String taskId, String step, String digest, @Nullable TaskStatus checked, Runnable send) {
        Turn before = turns.of(taskId);
        turns.opened(taskId, step, digest, checked);
        Turn opened = turns.of(taskId);
        try {
            send.run();
        } catch (RuntimeException failure) {
            boolean restored = turns.restored(taskId, opened, before);
            log.atError().setMessage("relay failed").addKeyValue("task", taskId).addKeyValue("step", step)
                    .addKeyValue("cause", failure.toString())
                    .addKeyValue("effect", restored ? "the turn put back, the task stopped for the human"
                            : "the task had moved on, left as it is")
                    .setCause(failure).log();
            if (restored) {
                door.undelivered(taskId, step, failure.toString());
            }
            throw failure;
        }
    }

    private void undone(String taskId, String step, Claim claim, Turn opened, RuntimeException failure) {
        boolean undone = claim.undo().test(opened);
        log.atError().setMessage("claimed step failed").addKeyValue("task", taskId).addKeyValue("step", step)
                .addKeyValue("cause", failure.toString())
                .addKeyValue("effect", undone ? "the claim undone, the task stopped for the human"
                        : "the task had moved on, left as it is")
                .setCause(failure).log();
        if (undone) {
            door.undelivered(taskId, step, failure.toString());
        }
    }

    /** Throws where the task moved on since the claim's read, before any door records a miss against it. */
    public void requireStanding(String taskId, Step step, Claim claim) {
        if (!claim.standing().getAsBoolean()) {
            throw dropped(taskId, step.id(), claim);
        }
    }

    /** Throws where the task no longer stands where the caller read it, before any door records a miss against it. */
    private void requireStanding(String taskId, String step, Standing read) {
        Standing now = standing(taskId);
        if (!now.equals(read)) {
            throw dropped(taskId, step, read.turn(), now.turn());
        }
    }

    private TaskMovedOn dropped(String taskId, String step, Claim claim) {
        return dropped(taskId, step, claim.read().turnOrNone().id(), turns.of(taskId).id());
    }

    private TaskMovedOn dropped(String taskId, String step, long turn, long turnNow) {
        log.atInfo().setMessage("step dropped").addKeyValue("task", taskId).addKeyValue("step", step)
                .addKeyValue("turn", turn).addKeyValue("turnNow", turnNow)
                .addKeyValue("cause", "the task moved on while the step was decided")
                .addKeyValue("effect", "nothing relayed, the status left as it is")
                .log();
        return new TaskMovedOn(taskId);
    }

    private String told(Step step, String facts) {
        return brief.section(step).filter(ask -> !ask.isBlank()).map(ask -> facts + "\n\n" + ask).orElse(facts);
    }
}
