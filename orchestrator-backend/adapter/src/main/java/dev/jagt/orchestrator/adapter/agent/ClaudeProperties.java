package dev.jagt.orchestrator.adapter.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** One agent's own settings, under its own key: the generic install config carries no vendor name. */
@ConfigurationProperties(prefix = "orchestrator.claude")
public record ClaudeProperties(String command, String mcpConfig, String outputStyle, List<String> disabledPlugins) {

    public ClaudeProperties {
        command = command == null || command.isBlank() ? "claude" : command;
        mcpConfig = mcpConfig == null ? "" : mcpConfig.strip();
        outputStyle = outputStyle == null ? "" : outputStyle.strip();
        disabledPlugins = disabledPlugins == null ? List.of() : List.copyOf(disabledPlugins);
    }

    public static ClaudeProperties defaults() {
        return new ClaudeProperties("", "", "", List.of());
    }
}
