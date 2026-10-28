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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** The one writer of a task's stop: tripped by the loop breaker or a miss, announced once, cleared by a lift. */
@Service
@RequiredArgsConstructor
public class TaskStops {

    public enum Relay { RELAY, UNCHANGED, ASKING, STOPPED }

    private final StateService stateService;
    private final LoopBreaker.Current breakers;
    private final StopAnnouncements announcements;
    private final Clock clock;

    public void stop(String taskId, TaskStop.Trigger trigger, String evidence) {
        trip(taskId, task -> task, task -> Optional.of(new TaskStop(trigger, clock.millis(), evidence)));
    }

    /** False where a round of {@code step} would run past its ceiling, which stops the task, or a stop stands. */
    public boolean roundDue(String taskId, String step) {
        LoopBreaker breaker = breakers.current();
        return trip(taskId, task -> task,
                task -> breaker.beforeRound(step, task.turnOrNone(), task.trailOrNone(), clock.millis())).isEmpty();
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
        if (task.report().asks()) {
            return Relay.ASKING;
        }
        RoundTrail trail = task.trailOrNone();
        return trail.relayed(digest) || trail.noNewNote(notes, failing) ? Relay.UNCHANGED : Relay.RELAY;
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
