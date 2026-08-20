package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
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

    /**
     * Whether the poller has any business with this task at all. AN OPEN REQUEST IS THE WHOLE CONDITION, never a
     * status: a reviewer writes on a request whatever the task is doing meanwhile, so a round handed back
     * (REVIEW_PENDING), a task the human sent back to work (IN_PROGRESS) and one already deployed are all still
     * reviewable — reading a round costs one host call and relays drafts, which no status makes wrong. Gating on
     * CI_POLLING/REVIEWED meant every comment written after the agent handed the round back reached nobody until a
     * human typed `sweep`. DONE is the one status that ends it: the worktree is gone, so there is nothing to relay
     * a round into.
     */
    public boolean polls(TaskState task) {
        // ANY repository's request, the same question the sweep and the projection ask.
        return enabled && task.hasReviewRequest() && task.status() != TaskStatus.DONE;
    }

    /** What a human is owed about this task: is anything watching it, and when will it next look. */
    public AutoReviewWatch watch(TaskState task, long now) {
        if (!polls(task)) {
            return AutoReviewWatch.none();
        }
        if (!task.autoReviewEnabled(true)) {
            return AutoReviewWatch.offForTask();
        }
        // The round's own stamp when there is one — every ship re-arms it, which is what makes the window per
        // ROUND — and otherwise when the request was opened: a request adopted by `resume`, or reported from a
        // status that starts no round, is still a request somebody is reviewing. With neither, nothing can be
        // timed and that is said out loud rather than rendered as silence.
        long roundStart = task.mrCreatedAt() > 0 ? task.mrCreatedAt() : task.requestOpenedAt();
        if (roundStart == 0) {
            return AutoReviewWatch.noRound();
        }
        Duration interval = pollInterval(Duration.ofMillis(now - roundStart));
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
