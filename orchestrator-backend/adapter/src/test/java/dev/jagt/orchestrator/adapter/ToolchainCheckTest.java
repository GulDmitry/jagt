package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolchainCheckTest {

    @Test
    void refusesToStartWithoutTheTmuxEveryAgentRunsIn() {
        OrchestratorProperties properties = OrchestratorProperties.defaults().withTmuxCommand("no-such-tmux");

        assertThat(new ToolchainCheck(properties).problems())
                .anySatisfy(problem -> assertThat(problem)
                        .contains("orchestrator.tmux-command", "no-such-tmux"));
    }
}
