package dev.jagt.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "orchestrator.usage-limits")
public record UsageLimitsProperties(Boolean enabled, Duration every, List<Integer> thresholds,
                                    List<Integer> paidThresholds, Boolean wakeOnReset) {

    private static final Duration DEFAULT_EVERY = Duration.ofMinutes(30);
    private static final List<Integer> DEFAULT_THRESHOLDS = List.of(80, 95);

    public UsageLimitsProperties {
        enabled = enabled == null || enabled;
        every = every == null ? DEFAULT_EVERY : every;
        thresholds = thresholds == null ? DEFAULT_THRESHOLDS : List.copyOf(thresholds);
        paidThresholds = paidThresholds == null ? DEFAULT_THRESHOLDS : List.copyOf(paidThresholds);
        wakeOnReset = wakeOnReset != null && wakeOnReset;
    }

    public static UsageLimitsProperties defaults() {
        return new UsageLimitsProperties(true, DEFAULT_EVERY, DEFAULT_THRESHOLDS, DEFAULT_THRESHOLDS, false);
    }

    public UsageLimitsProperties withWakeOnReset(boolean wakeOnReset) {
        return new UsageLimitsProperties(enabled, every, thresholds, paidThresholds, wakeOnReset);
    }

    /** A wake needs a read of the limits, so it follows {@code enabled}. */
    public boolean wakes() {
        return enabled && wakeOnReset;
    }
}
