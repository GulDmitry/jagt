package dev.jagt.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the Codex agent runtime ({@code orchestrator.agent=codex}).
 *
 * <p>Its binary lives here rather than next to {@code orchestrator.claude-command} on purpose: that key is
 * NOT the Claude runtime's, it is the {@code claude} CLI the master assistant shells out to whatever agent is
 * active. A runtime's own settings belong to the runtime — that is what makes adding one a single class.
 *
 * @param command the Codex CLI binary (default {@code codex})
 */
@ConfigurationProperties(prefix = "orchestrator.codex")
public record CodexProperties(String command) {

    public CodexProperties {
        command = command == null || command.isBlank() ? "codex" : command.strip();
    }

    public static CodexProperties defaults() {
        return new CodexProperties(null);
    }
}
