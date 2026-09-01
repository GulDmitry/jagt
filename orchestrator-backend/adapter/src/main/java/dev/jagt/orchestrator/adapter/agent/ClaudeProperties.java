package dev.jagt.orchestrator.adapter.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** One agent's own settings, under its own key: the generic install config carries no vendor name. */
@ConfigurationProperties(prefix = "orchestrator.claude")
public record ClaudeProperties(String command) {

    public ClaudeProperties {
        command = command == null || command.isBlank() ? "claude" : command;
    }

    public static ClaudeProperties defaults() {
        return new ClaudeProperties(null);
    }
}
