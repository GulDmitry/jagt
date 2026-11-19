package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EveryTestAssertsTest {

  @Test
  void everyTestMethodReachesAnAssertion() {
    assertThat(PmdFindings.PROCESSING_ERRORS).isEmpty();
    assertThat(PmdFindings.ASSERTIONLESS_TESTS).isEmpty();
  }
}
