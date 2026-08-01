package dev.jagt.orchestrator.agent;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Claude Code runtime — the default agent. Launches the {@code claude} CLI in the worktree window.
 * (Worktree provisioning + the MCP callback wiring land here next, behind {@link AgentRuntime}.)
 * Selected by {@code orchestrator.agent=claude} (the default).
 */
@Component
@ConditionalOnProperty(name = "orchestrator.agent", havingValue = "claude", matchIfMissing = true)
public class ClaudeAgentRuntime extends AbstractAgentRuntime {

    private final OrchestratorProperties properties;

    public ClaudeAgentRuntime(OrchestratorProperties properties) {
        this.properties = properties;
    }

    @Override
    public String displayName() {
        return "Claude";
    }

    @Override
    public String launchCommand(boolean planMode) {
        return properties.claudeCommand()
                + (planMode ? " --permission-mode plan" : "")
                + " " + shellQuote(properties.agentPrompt());
    }
}
