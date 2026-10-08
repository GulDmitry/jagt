package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.task.AssistantCallKind;
import lombok.With;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@With
@ConfigurationProperties(prefix = "orchestrator.assistant")
public record AssistantProperties(String settingSources, String model,
                                  List<String> allowedTools, String mcpConfig,
                                  Map<AssistantCallKind, String> mcpConfigByKind) {

    public AssistantProperties {
        settingSources = settingSources == null || settingSources.isBlank() ? "user,project,local" : settingSources;
        allowedTools = allowedTools == null ? List.of() : allowedTools;
        // A list would be bound by splitting a scalar on commas, which cuts a declaration written inline in half.
        mcpConfig = mcpConfig == null ? "" : mcpConfig;
        mcpConfigByKind = mcpConfigByKind == null ? Map.of() : Map.copyOf(mcpConfigByKind);
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

    /** The Master's review loads an MCP server only where one of these is named. */
    public List<String> mcpTools() {
        return allowedTools.stream().filter(tool -> tool.startsWith("mcp__")).toList();
    }

    public static AssistantProperties empty() {
        return new AssistantProperties(null, null, null, null, null);
    }
}
