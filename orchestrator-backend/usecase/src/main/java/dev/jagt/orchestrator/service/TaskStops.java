package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.task.LoopWatch;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** A task's stop, announced the once it is new; a stop standing is kept until a human lifts it. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskStops {

    private final StateService stateService;
    private final Notifications notifications;
    private final Clock clock;

    public void stop(String taskId, LoopWatch.Trigger trigger, String evidence) {
        trip(taskId, task -> task.loopOrNone().stopped(new LoopWatch.Stop(trigger, clock.millis(), evidence)));
    }

    /** The stop standing once {@code next} ran. */
    public Optional<LoopWatch.Stop> trip(String taskId, Function<TaskState, LoopWatch> next) {
        AtomicReference<LoopWatch.@Nullable Stop> standing = new AtomicReference<>();
        AtomicReference<LoopWatch.@Nullable Stop> tripped = new AtomicReference<>();
        stateService.updateTask(taskId, task -> {
            if (task.loopStopped()) {
                standing.set(task.loopOrNone().stop());
                return task;
            }
            LoopWatch watch = next.apply(task);
            tripped.set(watch.stop());
            standing.set(watch.stop());
            return watch.equals(task.loopOrNone()) ? task : task.withLoop(watch);
        });
        Optional.ofNullable(tripped.get()).ifPresent(stop -> announce(taskId, stop));
        return Optional.ofNullable(standing.get());
    }

    private void announce(String taskId, LoopWatch.Stop stop) {
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
