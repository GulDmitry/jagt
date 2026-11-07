package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.port.Problem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolchainCheckTest {

    @Test
    void refusesToStartWithoutTheTmuxEveryAgentRunsIn() {
        ToolchainProperties properties = ToolchainProperties.defaults().withTmuxCommand("no-such-tmux");

        assertThat(new ToolchainCheck(properties).problems()).extracting(Problem::key)
                .contains("orchestrator.tmux-command");
    }

    @Test
    void raisesNoVersionControlProblemWhereItsToolIsOnPath() {
        ToolchainProperties properties = ToolchainProperties.defaults().withTmuxCommand("no-such-tmux");

        assertThat(new ToolchainCheck(properties).problems()).extracting(Problem::key).doesNotContain("git");
    }

    @Test
    void raisesNoAgentProcessProblemWhereItsToolIsOnPath() {
        ToolchainProperties properties = ToolchainProperties.defaults().withTmuxCommand("no-such-tmux");

        assertThat(new ToolchainCheck(properties).problems()).extracting(Problem::key).doesNotContain("pgrep");
    }

    @Test
    void notesNothingAboutLeftoverProcessesWhereTheirToolIsOnPath() {
        assertThat(new ToolchainCheck(ToolchainProperties.defaults()).notes()).isEmpty();
    }
}
