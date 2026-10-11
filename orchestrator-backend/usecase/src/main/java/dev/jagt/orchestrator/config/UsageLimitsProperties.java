package dev.jagt.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "orchestrator.usage-limits")
public record UsageLimitsProperties(Duration every, List<Integer> thresholds, List<Integer> paidThresholds) {

    public UsageLimitsProperties {
        every = every == null ? Duration.ofMinutes(30) : every;
        thresholds = thresholds == null ? List.of(80, 95) : List.copyOf(thresholds);
        paidThresholds = paidThresholds == null ? List.of(80, 95) : List.copyOf(paidThresholds);
    }

    public static UsageLimitsProperties defaults() {
        return new UsageLimitsProperties(null, null, null);
    }
}
