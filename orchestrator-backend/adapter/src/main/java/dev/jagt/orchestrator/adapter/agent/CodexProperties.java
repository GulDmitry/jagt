package dev.jagt.orchestrator.adapter.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The Codex runtime's binary only; {@code orchestrator.claude.command} is the master assistant's CLI as well. */
@ConfigurationProperties(prefix = "orchestrator.codex")
public record CodexProperties(String command) {

    public CodexProperties {
        command = command == null || command.isBlank() ? "codex" : command.strip();
    }

    public static CodexProperties defaults() {
        return new CodexProperties(null);
    }
}
