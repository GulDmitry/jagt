package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import lombok.RequiredArgsConstructor;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AutoReviewConfig;

import java.time.Duration;

/**
 * The whole auto-review policy: whether polling runs at all, how long to wait before the next poll given how long
 * the request has been open, and what that means for one task. The interval escalates LINEARLY from {@code min}
 * at the window start to {@code max} at its end — poll often early, back off as the request ages — and past the
 * window polling stops.
 *
 * <p>The poller and every human surface ask the SAME object, so a dashboard cannot advertise a poll the
 * scheduler will not make.
 */
@RequiredArgsConstructor
public final class AutoReviewCadence {

    private final boolean enabled;
    private final Duration window;
    private final long minMinutes;
    private final long maxMinutes;

    public static AutoReviewCadence from(AutoReviewConfig cfg) {
        return new AutoReviewCadence(cfg.enabledOrDefault(), Duration.ofHours(cfg.windowHoursOrDefault()),
                cfg.minIntervalMinutesOrDefault(), cfg.maxIntervalMinutesOrDefault());
    }

    public boolean enabled() {
        return enabled;
    }

    public long windowHours() {
        return window.toHours();
    }

    /** What a human is owed about this task: is anything watching it, and when will it next look. */
    public AutoReviewWatch watch(TaskState task, long now) {
        if (!enabled || task.status() != TaskStatus.CI_POLLING) {
            return AutoReviewWatch.none();
        }
        if (!task.autoReviewEnabled(true)) {
            return AutoReviewWatch.offForTask();
        }
        // ANY repository's request, the same question the sweep and the projection ask.
        if (!task.hasReviewRequest()) {
            return AutoReviewWatch.none();
        }
        // A request with no round stamp cannot be timed, so nothing will ever poll it — said out loud rather than
        // rendered as silence, which is what a state.json written before the stamp existed would otherwise look
        // like.
        if (task.mrCreatedAt() == 0) {
            return AutoReviewWatch.noRound();
        }
        Duration interval = pollInterval(Duration.ofMillis(now - task.mrCreatedAt()));
        return interval == null
                ? AutoReviewWatch.windowElapsed(windowHours())
                : AutoReviewWatch.watching(task.lastPolledAt() + interval.toMillis());
    }

    /** How the cadence reads to a human, in the one place both surfaces take the words from. */
    public String summary() {
        return enabled ? "auto-review on" : "auto-review off";
    }

    /** The wait before the next poll for a request open this long, or {@code null} once the window has
     *  elapsed. */
    public Duration pollInterval(Duration elapsed) {
        if (elapsed.compareTo(window) > 0) {
            return null;
        }
        double fraction = (double) elapsed.toMinutes() / window.toMinutes();
        long minutes = Math.round(minMinutes + (maxMinutes - minMinutes) * fraction);
        return Duration.ofMinutes(Math.min(maxMinutes, Math.max(minMinutes, minutes)));
    }
}
