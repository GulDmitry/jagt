package dev.jagt.orchestrator.port;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.port.AcceptanceRunner.Verdict;
import org.junit.jupiter.api.Test;

class AcceptanceRunnerTest {

  @Test
  void aFlowIsUnprovableUntilARunProvesIt() {
    FlowResult result = FlowResult.defaults();

    assertThat(result.verdict()).isEqualTo(Verdict.UNPROVABLE);
  }
}
