package dev.jagt.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "orchestrator.usage-limits")
public record UsageLimitsProperties(Duration every, List<Integer> thresholds, List<Integer> paidThresholds) {

    private static final Duration DEFAULT_EVERY = Duration.ofMinutes(30);
    private static final List<Integer> DEFAULT_THRESHOLDS = List.of(80, 95);

    public UsageLimitsProperties {
        every = every == null ? DEFAULT_EVERY : every;
        thresholds = thresholds == null ? DEFAULT_THRESHOLDS : List.copyOf(thresholds);
        paidThresholds = paidThresholds == null ? DEFAULT_THRESHOLDS : List.copyOf(paidThresholds);
    }

    public static UsageLimitsProperties defaults() {
        return new UsageLimitsProperties(DEFAULT_EVERY, DEFAULT_THRESHOLDS, DEFAULT_THRESHOLDS);
    }
}
