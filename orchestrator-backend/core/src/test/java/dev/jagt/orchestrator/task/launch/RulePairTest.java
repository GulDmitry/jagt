package dev.jagt.orchestrator.task.launch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RulePairTest {

  @Test
  void stripsWhatTheRouterPaddedTheRulePairWith() {
    RulePair pair = new RulePair(" labels carry it ", " other ");

    assertThat(pair.kept()).isEqualTo("labels carry it");
    assertThat(pair.duplicate()).isEqualTo("other");
  }
}
