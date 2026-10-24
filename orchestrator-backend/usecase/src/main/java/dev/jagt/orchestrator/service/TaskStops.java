package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.task.LoopBreaker;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.RoundTrail;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** The one writer of a task's stop: tripped by the loop breaker or a miss, announced once, cleared by a lift. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskStops {

    public enum Relay { RELAY, UNCHANGED, STOPPED }

    private final StateService stateService;
    private final LoopBreaker.Current breakers;
    private final Notifications notifications;
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

    /** A round identical to the last one relayed is not news, whatever was relayed in between. */
    public Relay round(String taskId, ReviewFacts facts, String brief) {
        String digest = Turn.digestOf(brief);
        AtomicBoolean unchanged = new AtomicBoolean();
        LoopBreaker breaker = breakers.current();
        Optional<TaskStop> stop = trip(taskId, task -> {
            RoundTrail trail = task.trailOrNone();
            unchanged.set(trail.relayed(digest));
            return unchanged.get() ? task : task.withTrail(trail.withRound(
                    new RoundTrail.Round(clock.millis(), facts.threads().size(), failing(facts), digest)));
        }, task -> unchanged.get() ? Optional.empty()
                : breaker.afterRound(task.trailOrNone(), task.history(), clock.millis()));
        return stop.isPresent() ? Relay.STOPPED : unchanged.get() ? Relay.UNCHANGED : Relay.RELAY;
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
        stateService.updateTask(taskId, task -> {
            if (task.stopped()) {
                standing.set(task.stop());
                return task;
            }
            TaskState recorded = record.apply(task);
            Optional<TaskStop> stop = check.apply(recorded);
            stop.ifPresent(tripped::set);
            stop.ifPresent(standing::set);
            return stop.map(recorded::withStop).orElse(recorded);
        });
        Optional.ofNullable(tripped.get()).ifPresent(stop -> announce(taskId, stop));
        return Optional.ofNullable(standing.get());
    }

    private static String failing(ReviewFacts facts) {
        return Pipeline.of(facts.pipelineStatus()) == Pipeline.RED
                ? facts.pipelineFailure().lines().findFirst().orElse("") : "";
    }


    private void announce(String taskId, TaskStop stop) {
        String alias = stateService.task(taskId).map(TaskState::alias).orElse(taskId);
        log.atWarn().setMessage("task stopped").addKeyValue("task", taskId).addKeyValue("alias", alias)
                .addKeyValue("cause", stop.trigger().name().toLowerCase(Locale.ROOT))
                .addKeyValue("said", OneLine.clipped(stop.evidence()))
                .addKeyValue("effect", "auto-review and the master leave the task")
                .addKeyValue("fix", "lift " + alias)
                .log();
        notifications.send(new Notification(Notification.Topic.INSTALL, taskId, "jagt stopped a task", stop.reading()));
    }
}
