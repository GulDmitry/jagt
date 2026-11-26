package dev.jagt.orchestrator.adapter.tmux;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.port.host.Problem;
import org.junit.jupiter.api.Test;

class TmuxCheckTest {

  @Test
  void refusesToStartWithoutTheTmuxEveryAgentRunsIn() {
    ToolchainProperties properties = ToolchainProperties.defaults().withTmuxCommand("no-such-tmux");

    assertThat(new TmuxCheck(properties).problems())
        .extracting(Problem::key)
        .contains("orchestrator.tmux-command");
  }

  @Test
  void raisesNoAgentProcessProblemWhereItsToolIsOnPath() {
    ToolchainProperties properties = ToolchainProperties.defaults().withTmuxCommand("no-such-tmux");

    assertThat(new TmuxCheck(properties).problems())
        .extracting(Problem::key)
        .doesNotContain("pgrep");
  }
}
