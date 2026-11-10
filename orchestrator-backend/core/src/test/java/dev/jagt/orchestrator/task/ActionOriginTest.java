package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActionOriginTest {

  @Test
  void labelsAnOriginWithTheDottedEyeWhateverTheMachineLocale() {
    assertThat(ActionOrigin.AUTO_REVIEW.label()).isEqualTo("auto-review");
  }
}
