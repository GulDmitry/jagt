package dev.jagt.orchestrator.adapter.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The scripted test runtime's script, given the worktree path and, in plan mode, {@code plan}; blank starts and exits. */
@ConfigurationProperties(prefix = "orchestrator.stub")
public record StubAgentProperties(String script) {

    public StubAgentProperties {
        script = script == null || script.isBlank() ? null : script.strip();
    }

    public static StubAgentProperties defaults() {
        return new StubAgentProperties(null);
    }
}
