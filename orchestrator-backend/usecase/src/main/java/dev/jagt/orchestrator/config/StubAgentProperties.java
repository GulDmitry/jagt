package dev.jagt.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the scripted test runtime ({@code orchestrator.agent.cli=stub}).
 *
 * @param script optional executable the stub runs in place of an agent — it receives the worktree path and,
 *               in plan mode, {@code plan}, and may drive the orchestrator over {@code POST /mcp} (the
 *               {@code X-Working-Directory} header is what scopes it to its task). Blank = an agent that
 *               starts and exits, which is all a provisioning/lifecycle assertion needs.
 */
@ConfigurationProperties(prefix = "orchestrator.stub")
public record StubAgentProperties(String script) {

    public StubAgentProperties {
        script = script == null || script.isBlank() ? null : script.strip();
    }

    public static StubAgentProperties defaults() {
        return new StubAgentProperties(null);
    }
}
