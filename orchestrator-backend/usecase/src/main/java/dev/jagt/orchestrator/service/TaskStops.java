package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.task.LoopBreaker;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.RoundTrail;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** The one writer of a task's stop: tripped by the loop breaker or a miss, announced once, cleared by a lift or by the read its trigger names. */
@Service
@RequiredArgsConstructor
public class TaskStops {

    public enum Relay { RELAY, UNCHANGED, ASKING, STOPPED }

    /** The refusals of a read that show the checks a stop waited on are gone; any other refusal says nothing about them. */
    private static final Set<ReviewSweepService.SweepResult.Kind> DISPROVING_REFUSALS =
            EnumSet.of(ReviewSweepService.SweepResult.Kind.GONE, ReviewSweepService.SweepResult.Kind.NO_MR);

    private final StateService stateService;
    private final LoopBreaker.Current breakers;
    private final StopAnnouncements announcements;
    private final Clock clock;

    /** The stop standing once this one was set: an earlier stop stands, and keeps its own words. */
    public Optional<TaskStop> stop(String taskId, TaskStop.Trigger trigger, String evidence) {
        return trip(taskId, task -> task, task -> Optional.of(new TaskStop(trigger, clock.millis(), evidence)));
    }

    /** False where a round of {@code step}, a move too, would run past a ceiling, which stops the task, or a stop stands. */
    public boolean roundDue(String taskId, String step) {
        LoopBreaker breaker = breakers.current();
        return trip(taskId, task -> task,
                task -> breaker.beforeRound(step, task.turnOrNone(), task.trailOrNone(), clock.millis())
                        .or(() -> breaker.beforeMove(task.history(), task.trailOrNone(), clock.millis()))).isEmpty();
    }

    /** False where one more unattended move would run past the hour's ceiling, which stops the task, or a stop stands. */
    public boolean moveDue(String taskId) {
        LoopBreaker breaker = breakers.current();
        return trip(taskId, task -> task,
                task -> breaker.beforeMove(task.history(), task.trailOrNone(), clock.millis())).isEmpty();
    }

    /** A round identical to the last one relayed, bringing no new note on green checks, or met by a standing question, is not news. */
    public Relay round(String taskId, ReviewFacts facts, String brief, String tip) {
        String notes = facts.noteFingerprint();
        String failing = failing(facts);
        String key = factKey(facts, failing, notes, tip);
        String digest = Turn.digestOf(key.isEmpty() ? brief : key);
        AtomicReference<Relay> verdict = new AtomicReference<>(Relay.RELAY);
        LoopBreaker breaker = breakers.current();
        Optional<TaskStop> stop = trip(taskId, task -> {
            verdict.set(verdictOn(task, digest, notes, failing));
            return verdict.get() != Relay.RELAY ? task : task.withTrail(task.trailOrNone().withRound(
                    new RoundTrail.Round(clock.millis(), facts.threads().size(), failing, digest, notes)));
        }, task -> verdict.get() != Relay.RELAY ? Optional.empty()
                : breaker.afterRound(task.trailOrNone(), task.history(), clock.millis()));
        return stop.isPresent() ? Relay.STOPPED : verdict.get();
    }

    private static Relay verdictOn(TaskState task, String digest, String notes, String failing) {
        RoundTrail trail = task.trailOrNone();
        if (task.report().asks() && !trail.newNotes(notes)) {
            return Relay.ASKING;
        }
        return trail.relayed(digest) || trail.noNewNote(notes, failing) ? Relay.UNCHANGED : Relay.RELAY;
    }

    /** Whether the agent's own question stands, which holds the checks back and nothing of the threads. */
    public boolean asking(String taskId) {
        return stateService.task(taskId).filter(task -> task.report().asks()).isPresent();
    }

    public boolean mayShip(String taskId) {
        LoopBreaker breaker = breakers.current();
        return trip(taskId, task -> task,
                task -> breaker.beforeShip(task.trailOrNone(), task.history(), clock.millis())).isEmpty();
    }

    public boolean stopped(String taskId) {
        return stateService.task(taskId).filter(TaskState::stopped).isPresent();
    }

    public void lift(String taskId) {
        stateService.updateTask(taskId, task -> task.lifted(clock.millis()));
    }

    /** The stop a read of the checks ended: one whose trigger ends on a read, and the read listed the checks, not red. */
    public Optional<TaskStop> endOnRead(String taskId, Pipeline read) {
        return endWhen(taskId, TaskStop.Trigger.Ends.HOST_READ, read != Pipeline.UNKNOWN && read != Pipeline.RED, "");
    }

    /** The stop that ended because the session's window runs again: one whose trigger ends on it. */
    public Optional<TaskStop> endOnSessionAlive(String taskId) {
        return endWhen(taskId, TaskStop.Trigger.Ends.SESSION_ALIVE, true, "");
    }

    /** The stop a session's report that is no wait ended: one whose trigger ends on it. */
    public Optional<TaskStop> endOnReport(String taskId) {
        return endWhen(taskId, TaskStop.Trigger.Ends.SESSION_REPORT, true, "");
    }

    /** Whether a stop of {@code trigger} stands on the task. */
    public boolean standing(String taskId, TaskStop.Trigger trigger) {
        return stateService.task(taskId).map(TaskState::stop).filter(stop -> stop.trigger() == trigger).isPresent();
    }

    /**
     * The stop a refused read ended: one whose trigger ends on a read, and the refusal says the checks are no more
     * on a request the stop was counted on; {@code request} is blank where the refusal is about none.
     */
    public Optional<TaskStop> endOnRefusal(String taskId, ReviewSweepService.SweepResult.Kind refusal, String request) {
        return endWhen(taskId, TaskStop.Trigger.Ends.HOST_READ, DISPROVING_REFUSALS.contains(refusal), request);
    }

    private Optional<TaskStop> endWhen(String taskId, TaskStop.Trigger.Ends ends, boolean disproves, String request) {
        AtomicReference<@Nullable TaskStop> ended = new AtomicReference<>();
        stateService.updateTask(taskId, task -> {
            TaskStop stop = task.stop();
            if (!disproves || stop == null || stop.trigger().ends() != ends
                    || !stop.countedOnOrNone(request)) {
                return task;
            }
            ended.set(stop);
            return task.lifted(clock.millis());
        });
        return Optional.ofNullable(ended.get());
    }

    /** The stop standing once {@code check} read the task. */
    public Optional<TaskStop> trip(String taskId, Function<TaskState, Optional<TaskStop>> check) {
        return trip(taskId, task -> task, check);
    }

    /** The stop standing once {@code record} ran and {@code check} read what it recorded. */
    private Optional<TaskStop> trip(String taskId, UnaryOperator<TaskState> record,
                                    Function<TaskState, Optional<TaskStop>> check) {
        AtomicReference<@Nullable TaskStop> standing = new AtomicReference<>();
        AtomicReference<@Nullable TaskStop> tripped = new AtomicReference<>();
        AtomicReference<@Nullable TaskState> stoppedTask = new AtomicReference<>();
        stateService.updateTask(taskId, task -> {
            if (task.stopped()) {
                standing.set(task.stop());
                return task;
            }
            TaskState recorded = record.apply(task);
            Optional<TaskStop> stop = check.apply(recorded);
            stop.ifPresent(tripped::set);
            stop.ifPresent(s -> stoppedTask.set(recorded));
            stop.ifPresent(standing::set);
            return stop.map(s -> recorded.withStop(s.countedOn(recorded.requests()))).orElse(recorded);
        });
        Optional.ofNullable(tripped.get())
                .ifPresent(stop -> announcements.announce(taskId, Objects.requireNonNull(stoppedTask.get()), stop));
        return Optional.ofNullable(standing.get());
    }

    /** Equal for one round read twice, the brief's wording aside; empty where a fact it turns on is unknown. */
    private static String factKey(ReviewFacts facts, String failing, String notes, String tip) {
        boolean red = Pipeline.of(facts.pipelineStatus()) == Pipeline.RED;
        boolean unknown = red && (failing.isBlank() || tip.isBlank())
                || !facts.threads().isEmpty() && notes.isBlank() || !red && facts.threads().isEmpty();
        return unknown ? "" : (red ? failing + "@" + tip : "") + "\n" + notes;
    }

    private static String failing(ReviewFacts facts) {
        return Pipeline.of(facts.pipelineStatus()) == Pipeline.RED
                ? facts.pipelineFailure().lines().findFirst().orElse("") : "";
    }
}
