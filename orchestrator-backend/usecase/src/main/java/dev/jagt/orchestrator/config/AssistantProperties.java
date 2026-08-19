package dev.jagt.orchestrator.config;

import lombok.With;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@With
@ConfigurationProperties(prefix = "orchestrator.assistant")
public record AssistantProperties(String settingSources, String model, String permissionMode,
                                  List<String> allowedTools, String mcpConfig) {

    public AssistantProperties {
        settingSources = settingSources == null || settingSources.isBlank() ? "user,project,local" : settingSources;
        allowedTools = allowedTools == null ? List.of() : allowedTools;
        // A list would be bound by splitting a scalar on commas, which cuts a declaration written inline in half.
        mcpConfig = mcpConfig == null ? "" : mcpConfig;
    }

    public static AssistantProperties empty() {
        return new AssistantProperties(null, null, null, null, null);
    }
}
