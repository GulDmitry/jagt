package dev.jagt.orchestrator.adapter.flows;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.Problem;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlowsCheckTest {

  @Test
  void refusesAFlowsDriverJagtDoesNotCarry() {
    FlowsProperties selenium =
        new FlowsProperties("selenium", "", List.of(), Duration.ofMinutes(1));

    assertThat(new FlowsCheck(selenium).problems())
        .extracting(Problem::key)
        .containsExactly("orchestrator.roles.qa.flows.driver");
  }

  @Test
  void acceptsEachDriverJagtCarries() {
    assertThat(new FlowsCheck(new FlowsProperties("playwright", "", List.of(), null)).problems())
        .isEmpty();
    assertThat(new FlowsCheck(new FlowsProperties(null, "", List.of(), null)).problems()).isEmpty();
  }
}
