package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseChoicesTest {

    @Test
    void offersEveryPhaseWithItsLabel() {
        assertThat(Phase.choices()).contains(new Phase.Choice("DEPLOY", "deploy"), new Phase.Choice("DONE", "done"));
        assertThat(Phase.choices()).hasSize(Phase.values().length);
    }
}
