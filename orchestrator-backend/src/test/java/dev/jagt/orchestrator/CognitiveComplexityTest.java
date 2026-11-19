package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CognitiveComplexityTest {

  @Test
  void noMethodRunsPastTheCognitiveComplexityCeiling() {
    assertThat(PmdFindings.PROCESSING_ERRORS).isEmpty();
    assertThat(PmdFindings.COGNITIVE_VIOLATIONS).isEmpty();
  }
}
