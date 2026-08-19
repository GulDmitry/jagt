package dev.jagt.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * One agent's own settings, under its own key. The generic install config carries no vendor name: which binary
 * Claude is launched by is Claude's business, exactly as {@link CodexProperties} is Codex's.
 */
@ConfigurationProperties(prefix = "orchestrator.claude")
public record ClaudeProperties(String command) {

    public ClaudeProperties {
        command = command == null || command.isBlank() ? "claude" : command;
    }

    public static ClaudeProperties defaults() {
        return new ClaudeProperties(null);
    }
}
