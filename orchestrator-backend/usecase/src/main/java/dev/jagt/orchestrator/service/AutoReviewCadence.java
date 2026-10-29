package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AutoReviewConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * The whole auto-review policy. The interval escalates LINEARLY from {@code min} at the window start to {@code max}
 * at its end, and past the window polling stops; a request only a reviewer can move on waits {@code max} through the
 * window and then lengthens to {@code waitingMax} over a second window, never stopping, since nothing jagt reads
 * without a model says a reviewer wrote. The poller and every human surface ask the SAME
 * object, so a dashboard cannot advertise a poll the scheduler will not make.
 */
@RequiredArgsConstructor
public final class AutoReviewCadence {

    private final boolean enabled;
    private final Duration window;
    private final long minMinutes;
    private final long maxMinutes;
    private final long waitingMaxMinutes;

    public static AutoReviewCadence from(AutoReviewConfig cfg) {
        return new AutoReviewCadence(cfg.enabledOrDefault(), Duration.ofHours(cfg.windowHoursOrDefault()),
                cfg.minIntervalMinutesOrDefault(), cfg.maxIntervalMinutesOrDefault(),
                cfg.waitingMaxIntervalMinutesOrDefault());
    }

    public boolean enabled() {
        return enabled;
    }

    public long windowHours() {
        return window.toHours();
    }

    /**
     * Whether the poller has any business with this task at all. AN OPEN REQUEST IS THE WHOLE CONDITION, never a
     * status: a reviewer writes on a request whatever the task is doing meanwhile. DONE is the one status that ends
     * it — the worktree is gone, so there is nothing to relay a round into. A request already merged has no round
     * left to read.
     */
    public boolean polls(TaskState task) {
        // ANY repository's request, the same question the sweep and the projection ask.
        return enabled && task.hasReviewRequest() && !task.requestsMerged()
                && !FlowRules.closed(task.status());
    }

    /** What a human is owed about this task: is anything watching it, and when will it next look. */
    public AutoReviewWatch watch(TaskState task, long now) {
        TaskStop stop = task.stop();
        if (stop != null) {
            return AutoReviewWatch.stopped(stop);
        }
        if (!polls(task)) {
            return AutoReviewWatch.none();
        }
        if (!task.autoReviewEnabled(true)) {
            return AutoReviewWatch.offForTask();
        }
        // The round's own stamp when there is one, every ship re-arming it, and otherwise when the request was
        // opened. With neither, nothing can be timed and that is said out loud.
        long roundStart = task.mrCreatedAt() > 0 ? task.mrCreatedAt() : task.requestOpenedAt();
        if (roundStart == 0) {
            return AutoReviewWatch.noRound();
        }
        Duration elapsed = Duration.ofMillis(now - roundStart);
        Duration interval = FlowRules.movedOnByAReviewerAlone(task.status()) ? waitingInterval(elapsed) : pollInterval(elapsed);
        if (interval == null) {
            return AutoReviewWatch.windowElapsed(windowHours());
        }
        return AutoReviewWatch.watching(task.lastPolledAt() + interval.toMillis());
    }

    /** How the cadence reads to a human, in the one place both surfaces take the words from. */
    public String summary() {
        return enabled ? "auto-review on" : "auto-review off";
    }

    /** The wait for a request only a reviewer moves on: {@code max} in the window, then up to {@code waitingMax}. */
    Duration waitingInterval(Duration elapsed) {
        double past = Math.min(1.0, Math.max(0.0, (double) (elapsed.toMinutes() - window.toMinutes()) / window.toMinutes()));
        return Duration.ofMinutes(Math.round(maxMinutes + (waitingMaxMinutes - maxMinutes) * past));
    }

    /** The wait before the next poll for a request open this long, or {@code null} once the window has
     *  elapsed. */
    public @Nullable Duration pollInterval(Duration elapsed) {
        if (elapsed.compareTo(window) > 0) {
            return null;
        }
        double fraction = (double) elapsed.toMinutes() / window.toMinutes();
        long minutes = Math.round(minMinutes + (maxMinutes - minMinutes) * fraction);
        return Duration.ofMinutes(Math.min(maxMinutes, Math.max(minMinutes, minutes)));
    }
}
