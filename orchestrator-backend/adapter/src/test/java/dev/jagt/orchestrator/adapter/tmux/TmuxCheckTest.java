package dev.jagt.orchestrator.adapter.tmux;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.host.Problem;
import org.junit.jupiter.api.Test;

class TmuxCheckTest {

  @Test
  void refusesToStartWithoutTheTmuxEveryAgentRunsIn() {
    TmuxProperties properties = new TmuxProperties("no-such-tmux", "");

    assertThat(new TmuxCheck(properties).problems())
        .extracting(Problem::key)
        .contains("orchestrator.tmux.command");
  }

  @Test
  void raisesNoAgentProcessProblemWhereItsToolIsOnPath() {
    TmuxProperties properties = new TmuxProperties("no-such-tmux", "");

    assertThat(new TmuxCheck(properties).problems())
        .extracting(Problem::key)
        .doesNotContain("pgrep");
  }
}
