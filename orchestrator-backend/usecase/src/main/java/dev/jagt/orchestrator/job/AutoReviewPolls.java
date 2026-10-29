package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.ReviewSweepService;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.ActionOrigin;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Predicate;

@Component
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class AutoReviewPolls {

    private final ReviewSweepService reviewSweep;
    private final Notifications notifications;
    private final TaskStops stops;
    @Qualifier("autoReviewSweeps")
    private final Executor executor;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> unreadableNotified = ConcurrentHashMap.newKeySet();

    /**
     * Stops the tick from QUEUING polls behind a sweep that runs for minutes. It does NOT make a task's sweeps
     * mutually exclusive; that exclusion lives where every trigger passes through. {@code finished} runs last and
     * unconditionally: losing it would exclude the task from auto-review for the JVM's life.
     */
    boolean start(String taskId, @Nullable String alias, long round, Runnable finished) {
        if (!stops.moveDue(taskId) || !inFlight.add(taskId)) {
            return false;
        }
        log.atInfo().setMessage("auto-review poll due").addKeyValue("task", taskId)
                .addKeyValue("alias", alias)
                .log();
        try {
            executor.execute(() -> pollNow(taskId, round, finished));
            return true;
        } catch (RejectedExecutionException e) {
            // Caught NARROWLY: an exception from the sweep landing here would leave lastPolledAt unadvanced
            // under a same-thread executor, re-running the sweep forever.
            inFlight.remove(taskId);
            log.atWarn().setMessage("auto-review poll not scheduled")
                    .addKeyValue("task", taskId)
                    .addKeyValue("cause", e.toString())
                    .log();
            return false;
        }
    }

    void rearmUnreadable(Predicate<String> marker) {
        unreadableNotified.removeIf(marker);
    }

    private void pollNow(String taskId, long round, Runnable finished) {
        try {
            ReviewSweepService.SweepResult result = OriginContext.as(ActionOrigin.AUTO_REVIEW,
                    () -> reviewSweep.sweep(taskId));
            // A round nobody could read is the one outcome an unattended poll must not keep to itself: the
            // retries are spent, and every further tick would say the same thing to the same log.
            if ((result.kind() == ReviewSweepService.SweepResult.Kind.UNREADABLE
                    || result.kind() == ReviewSweepService.SweepResult.Kind.GONE)
                    && unreadableNotified.add(taskId + "@" + round)) {
                notifications.send(Notification.fromAgent(taskId, "auto-review", result.message()));
            }
        } catch (RuntimeException e) {
            log.atWarn().setMessage("auto-review sweep failed")
                    .addKeyValue("task", taskId)
                    .addKeyValue("cause", e.toString())
                    .log();
        } finally {
            try {
                finished.run();
            } finally {
                inFlight.remove(taskId);
            }
        }
    }
}
