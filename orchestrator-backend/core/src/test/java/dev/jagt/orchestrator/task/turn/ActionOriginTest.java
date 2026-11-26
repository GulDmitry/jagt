package dev.jagt.orchestrator.task.turn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ActionOriginTest {

  @Test
  void labelsAnOriginWithTheDottedEyeWhateverTheMachineLocale() {
    assertThat(ActionOrigin.AUTO_REVIEW.label()).isEqualTo("auto-review");
  }

  @Test
  void countsExactlyTheSixOriginsNobodyAskedForAsUnattended() {
    var unattended = Arrays.stream(ActionOrigin.values()).filter(ActionOrigin::unattended).toList();

    assertThat(unattended)
        .containsExactlyInAnyOrder(
            ActionOrigin.AUTO_REVIEW,
            ActionOrigin.MASTER,
            ActionOrigin.ACCEPTANCE,
            ActionOrigin.TRACKER,
            ActionOrigin.JAGT,
            ActionOrigin.CODE_HOST);
  }
}
