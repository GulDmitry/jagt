package dev.jagt.orchestrator.port.worktree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.worktree.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.port.worktree.AcceptanceRunner.Verdict;
import org.junit.jupiter.api.Test;

class AcceptanceRunnerTest {

  @Test
  void flowIsUnprovableUntilRunProvesIt() {
    FlowResult result = FlowResult.defaults();

    assertThat(result.verdict()).isEqualTo(Verdict.UNPROVABLE);
  }
}
