package dev.jagt.orchestrator.config;

import org.jspecify.annotations.Nullable;
import dev.jagt.orchestrator.task.AssistantCallKind;
import lombok.With;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@With
@ConfigurationProperties(prefix = "orchestrator.assistant")
public record AssistantProperties(String settingSources, @Nullable String model,
                                  List<String> allowedTools, String mcpConfig,
                                  Map<AssistantCallKind, String> mcpConfigByKind, double maxCostUsd) {

    public static final double DEFAULT_MAX_COST_USD = 5.00;

    public AssistantProperties {
        settingSources = settingSources == null || settingSources.isBlank() ? "user,project,local" : settingSources;
        allowedTools = allowedTools == null ? List.of() : allowedTools;
        // A list would be bound by splitting a scalar on commas, which cuts a declaration written inline in half.
        mcpConfig = mcpConfig == null ? "" : mcpConfig;
        mcpConfigByKind = mcpConfigByKind == null ? Map.of() : Map.copyOf(mcpConfigByKind);
        maxCostUsd = maxCostUsd > 0 ? maxCostUsd : DEFAULT_MAX_COST_USD;
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
        return new AssistantProperties("", null, List.of(), "", Map.of(), 0);
    }
}
