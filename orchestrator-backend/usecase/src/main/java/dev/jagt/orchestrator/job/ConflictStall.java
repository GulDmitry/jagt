package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.ConflictWatch;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** A handed deploy conflict whose worktree stops moving is the human's: told once, kept in the task's state. */
@Component
@RequiredArgsConstructor
@Slf4j
class ConflictStall {

    private final StateService stateService;
    private final Notifications notifications;
    private final ConfigService config;
    private final Clock clock;

    boolean handed(String taskId) {
        return stateService.task(taskId).flatMap(TaskState::currentConflict).isPresent();
    }

    void watch(String taskId, DeployConflicts.WaitingConflict conflict) {
        long now = clock.millis();
        Duration after = config.load().conflictStallOrDefault();
        AtomicReference<ConflictWatch.@Nullable Told> told = new AtomicReference<>();
        stateService.updateTask(taskId, task -> {
            ConflictWatch watch = task.currentConflict()
                    .map(standing -> standing.seen(conflict.progress(), now))
                    .orElseGet(() -> ConflictWatch.handed(task.statusSince(), conflict.worktree().toString(),
                            conflict.progress(), now));
            if (watch.due(after, now)) {
                watch = watch.toldAt(now);
                told.set(watch.told());
            }
            return watch.equals(task.conflict()) ? task : task.withConflict(watch);
        });
        Optional.ofNullable(told.get()).ifPresent(stop -> announce(taskId, stop, after));
    }

    private void announce(String taskId, ConflictWatch.Told stop, Duration after) {
        String alias = stateService.task(taskId).map(TaskState::alias).orElse(taskId);
        log.atWarn().setMessage("deploy conflict stalled").addKeyValue("task", taskId).addKeyValue("alias", alias)
                .addKeyValue("cause", "no change in the deploy worktree for " + after.toMinutes() + " min")
                .addKeyValue("said", stop.reason())
                .addKeyValue("effect", "the human is told once; the deploy waits")
                .addKeyValue("fix", "resolve it in the deploy worktree, git add, then deploy " + alias)
                .log();
        notifications.send(Notification.watchdog(taskId, "deploy conflict stalled", stop.reason()));
    }
}
