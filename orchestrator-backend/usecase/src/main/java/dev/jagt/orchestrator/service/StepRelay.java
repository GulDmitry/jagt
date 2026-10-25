package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.flow.FlowReports;
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
 * session still holds the turn this step opened with the same facts.
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

    public boolean relay(String taskId, String step, String facts) {
        return opened(taskId, step, facts, false, told -> sessions.writeTaskContext(taskId, told));
    }

    public boolean relay(String taskId, String step, String facts, Claim claim) {
        return opened(taskId, step, facts, false, claim, told -> sessions.writeTaskContext(taskId, told));
    }

    /** A round sent back to work, false too where the rounds of this step running reached their ceiling. */
    public boolean relayRound(String taskId, String step, String facts) {
        return opened(taskId, step, facts, true, told -> sessions.writeTaskContext(taskId, told));
    }

    public boolean relayRound(String taskId, String step, String facts, Claim claim) {
        return opened(taskId, step, facts, true, claim, told -> sessions.writeTaskContext(taskId, told));
    }

    /** Written whatever the session already holds, as a hand-off that must land again. */
    public boolean write(String taskId, String step, String facts) {
        CaseDoor.Admission admission = door.opens(taskId, step);
        if (!admission.admitted()) {
            return false;
        }
        sessions.writeTaskContext(taskId, told(step, facts));
        turns.opened(taskId, step, Turn.digestOf(facts), admission.status());
        return true;
    }

    /** In a session started afresh, which reads the text with the task's notes rather than its conversation. */
    public boolean relayFresh(String taskId, String step, String facts) {
        return opened(taskId, step, facts, false, told -> sessions.relayFresh(taskId, told));
    }

    public boolean relayFresh(String taskId, String step, String facts, Claim claim) {
        return opened(taskId, step, facts, false, claim, told -> sessions.relayFresh(taskId, told));
    }

    /** Below an instruction still pending, which stands. */
    public boolean relayUnder(String taskId, String step, String facts, boolean fresh) {
        return opened(taskId, step, facts, false, told -> sessions.relayUnder(taskId, told, fresh));
    }

    public boolean relayUnder(String taskId, String step, String facts, boolean fresh, Claim claim) {
        return opened(taskId, step, facts, false, claim, told -> sessions.relayUnder(taskId, told, fresh));
    }

    /** Typed into the session's window, which takes one line. */
    public boolean say(String taskId, String step, String facts) {
        CaseDoor.Admission admission = door.opens(taskId, step);
        if (!admission.admitted()) {
            return false;
        }
        sessions.say(taskId, told(step, facts).replaceAll("\\s*\\n\\s*", " "));
        turns.opened(taskId, step, Turn.digestOf(facts), admission.status());
        return true;
    }

    /** A round of text already holding its steps' sections, sent afresh; every step has passed the door at {@code checked}. */
    public boolean relayComposed(String taskId, List<String> steps, String text, @Nullable TaskStatus checked) {
        String step = String.join("+", steps);
        String digest = Turn.digestOf(text);
        if (turns.of(taskId).holds(step, digest) || !door.roundDue(taskId, step)) {
            return false;
        }
        sessions.relayFresh(taskId, text);
        turns.opened(taskId, step, digest, checked);
        return true;
    }

    private boolean opened(String taskId, String step, String facts, boolean round, Consumer<String> send) {
        return opened(taskId, step, facts, round, null, send);
    }

    private boolean opened(String taskId, String step, String facts, boolean round, @Nullable Claim claim,
                           Consumer<String> send) {
        if (claim != null) {
            requireStanding(taskId, step, claim);
        }
        CaseDoor.Admission admission = door.opens(taskId, step);
        if (!admission.admitted()) {
            return false;
        }
        String digest = Turn.digestOf(facts);
        if (turns.of(taskId).holds(step, digest) || round && !door.roundDue(taskId, step)) {
            return false;
        }
        if (claim == null) {
            send.accept(told(step, facts));
            turns.opened(taskId, step, digest, admission.status());
            return true;
        }
        AtomicReference<Turn> opened = new AtomicReference<>();
        UnaryOperator<TaskState> opening = turns.opening(step, digest);
        FlowReports.Claimed claimed = claim.write().apply(task -> {
            TaskState written = opening.apply(task);
            opened.set(written.turnOrNone());
            return written;
        });
        if (claimed == FlowReports.Claimed.MOVED_ON) {
            throw dropped(taskId, step, claim);
        }
        if (claimed == FlowReports.Claimed.GONE) {
            return false;
        }
        turns.announced(taskId, step, admission.status());
        try {
            send.accept(told(step, facts));
        } catch (RuntimeException failure) {
            undone(taskId, step, claim, opened.get(), failure);
            throw failure;
        }
        return true;
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
    public void requireStanding(String taskId, String step, Claim claim) {
        if (!claim.standing().getAsBoolean()) {
            throw dropped(taskId, step, claim);
        }
    }

    private TaskMovedOn dropped(String taskId, String step, Claim claim) {
        log.atInfo().setMessage("step dropped").addKeyValue("task", taskId).addKeyValue("step", step)
                .addKeyValue("turn", claim.read().turnOrNone().id()).addKeyValue("turnNow", turns.of(taskId).id())
                .addKeyValue("cause", "the task moved on while the step was decided")
                .addKeyValue("effect", "nothing relayed, the status left as it is")
                .log();
        return new TaskMovedOn(taskId);
    }

    private String told(String step, String facts) {
        return brief.section(step).filter(ask -> !ask.isBlank()).map(ask -> facts + "\n\n" + ask).orElse(facts);
    }
}
