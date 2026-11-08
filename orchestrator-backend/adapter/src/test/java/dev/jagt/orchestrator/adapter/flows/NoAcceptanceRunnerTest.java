package dev.jagt.orchestrator.adapter.flows;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.port.AcceptanceRunner.Verdict;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoAcceptanceRunnerTest {

  @Test
  void everyFlowIsUnprovableWhenNoDriverIsConfigured() {
    List<FlowResult> results =
        new NoAcceptanceRunner().run(Path.of("/wt"), List.of("AC-1", "AC-2"), "https://alpha.test");

    assertThat(results).extracting(FlowResult::flow).containsExactly("AC-1", "AC-2");
    assertThat(results).extracting(FlowResult::verdict)
        .containsExactly(Verdict.UNPROVABLE, Verdict.UNPROVABLE);
  }
}
