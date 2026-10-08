package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionOriginTest {

    @Test
    void labelsAnOriginWithTheDottedIWhateverTheMachineLocale() {
        assertThat(ActionOrigin.AUTO_REVIEW.label()).isEqualTo("auto-review");
    }
}
