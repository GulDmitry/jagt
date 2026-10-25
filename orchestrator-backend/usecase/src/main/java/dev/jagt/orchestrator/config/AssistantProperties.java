package dev.jagt.orchestrator.config;

import org.jspecify.annotations.Nullable;
import dev.jagt.orchestrator.task.AssistantCallKind;
import lombok.With;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@With
@ConfigurationProperties(prefix = "orchestrator.assistant")
public record AssistantProperties(String settingSources, @Nullable String model,
                                  List<String> allowedTools, String mcpConfig,
                                  Map<AssistantCallKind, String> mcpConfigByKind, double maxCostUsd,
                                  int breakerFailures, Duration breakerCooldown) {

    public static final double DEFAULT_MAX_COST_USD = 1.75;
    public static final int DEFAULT_BREAKER_FAILURES = 3;
    public static final Duration DEFAULT_BREAKER_COOLDOWN = Duration.ofMinutes(5);

    public AssistantProperties {
        settingSources = settingSources == null || settingSources.isBlank() ? "user,project,local" : settingSources;
        allowedTools = allowedTools == null ? List.of() : allowedTools;
        // A list would be bound by splitting a scalar on commas, which cuts a declaration written inline in half.
        mcpConfig = mcpConfig == null ? "" : mcpConfig;
        mcpConfigByKind = mcpConfigByKind == null ? Map.of() : Map.copyOf(mcpConfigByKind);
        maxCostUsd = maxCostUsd > 0 ? maxCostUsd : DEFAULT_MAX_COST_USD;
        breakerFailures = breakerFailures > 0 ? breakerFailures : DEFAULT_BREAKER_FAILURES;
        breakerCooldown = breakerCooldown == null || breakerCooldown.isNegative() || breakerCooldown.isZero()
                ? DEFAULT_BREAKER_COOLDOWN : breakerCooldown;
    }

    /**
     * The servers one kind of read may load: its own declaration, else the one pinned for every call, else blank
     * for the human's whole configuration. A cheap read paying for every server in that configuration is what the
     * per-kind list exists to stop.
     */
    public String mcpConfigFor(AssistantCallKind kind) {
        String declared = mcpConfigByKind.get(kind);
        return declared == null || declared.isBlank() ? mcpConfig : declared;
    }

    public static AssistantProperties empty() {
        return new AssistantProperties("", null, List.of(), "", Map.of(), 0, 0, DEFAULT_BREAKER_COOLDOWN);
    }
}
