package dev.jagt.orchestrator.agent;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeAgentRuntimeTest {

    private static ClaudeAgentRuntime runtime(String command, String prompt) {
        return new ClaudeAgentRuntime(OrchestratorProperties.defaults()
                .withClaudeCommand(command).withAgentPrompt(prompt));
    }

    @Test
    void launchesTheClaudeCliWithTheBootstrapPromptQuoted() {
        assertThat(runtime("claude", "Read AGENTS.md and work").launchCommand(false))
                .isEqualTo("claude 'Read AGENTS.md and work'");
    }

    @Test
    void addsPlanModeFlagWhenRequested() {
        assertThat(runtime("claude", "go").launchCommand(true))
                .isEqualTo("claude --permission-mode plan 'go'");
    }

    @Test
    void escapesSingleQuotesInThePrompt() {
        assertThat(runtime("claude", "it's fine").launchCommand(false))
                .isEqualTo("claude 'it'\\''s fine'");
    }
}
