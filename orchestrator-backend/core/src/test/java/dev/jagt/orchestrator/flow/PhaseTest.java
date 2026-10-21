package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseTest {

    @Test
    void offersEveryPhaseUnderItsNameWithTheWordTheBoardShows() {
        assertThat(Phase.choices()).containsExactly(
                new Phase.Choice("BUILD", "build"), new Phase.Choice("REVIEW", "review"),
                new Phase.Choice("CHECK", "check"), new Phase.Choice("READY", "ready"),
                new Phase.Choice("DEPLOY", "deploy"), new Phase.Choice("DONE", "done"));
    }
}
