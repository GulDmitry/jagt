package dev.jagt.orchestrator.service;

import lombok.RequiredArgsConstructor;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AutoReviewConfig;

import java.time.Duration;

/**
 * The auto-review poll cadence: how long to wait before the next poll given how long the MR has been
 * open. Pure and total so it is unit-tested, not improvised. The interval escalates LINEARLY from
 * {@code min} at the window start to {@code max} at the window end (capped at {@code max} = hourly by
 * default): poll often early, back off as the MR ages. Past the window it returns {@code null} — polling
 * stops (the scheduler pings the human once to sweep manually).
 */
@RequiredArgsConstructor
public final class AutoReviewCadence {

    private final Duration window;
    private final long minMinutes;
    private final long maxMinutes;

    public static AutoReviewCadence from(AutoReviewConfig cfg) {
        return new AutoReviewCadence(Duration.ofHours(cfg.windowHoursOrDefault()),
                cfg.minIntervalMinutesOrDefault(), cfg.maxIntervalMinutesOrDefault());
    }

    /** The wait before the next poll for an MR open this long, or {@code null} once the window has elapsed. */
    public Duration pollInterval(Duration elapsed) {
        if (elapsed.compareTo(window) > 0) {
            return null;
        }
        double fraction = (double) elapsed.toMinutes() / window.toMinutes();
        long minutes = Math.round(minMinutes + (maxMinutes - minMinutes) * fraction);
        return Duration.ofMinutes(Math.min(maxMinutes, Math.max(minMinutes, minutes)));
    }
}
