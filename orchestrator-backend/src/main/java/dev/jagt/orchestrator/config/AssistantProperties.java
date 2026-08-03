package dev.jagt.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "orchestrator.assistant")
public record AssistantProperties(String settingSources, String model, String permissionMode, List<String> allowedTools) {

    public AssistantProperties {
        settingSources = settingSources == null || settingSources.isBlank() ? "user,project,local" : settingSources;
        allowedTools = allowedTools == null ? List.of() : allowedTools;
    }
}
