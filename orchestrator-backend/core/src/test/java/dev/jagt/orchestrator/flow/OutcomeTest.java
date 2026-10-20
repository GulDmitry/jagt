package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutcomeTest {

    @Test
    void sayingNothingHappenedCarriesNoStampAndDoesNotRefuse() {
        Outcome outcome = Outcome.nothing("already under way");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.NOTHING);
        assertThat(outcome.message()).isEqualTo("already under way");
        assertThat(outcome.stamp()).isNull();
        assertThat(outcome.kind().refuses()).isFalse();
    }

    @Test
    void relayingWorkToTheAgentCarriesTheStampTheTaskKeeps() {
        Outcome outcome = Outcome.relayed("handed over", "round 2 relayed");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
        assertThat(outcome.stamp()).isEqualTo("round 2 relayed");
    }

    @Test
    void stoppingOnAConflictCarriesTheStampAndDoesNotRefuse() {
        Outcome outcome = Outcome.conflict("conflict in the deploy worktree", "deploy conflict");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.CONFLICT);
        assertThat(outcome.stamp()).isEqualTo("deploy conflict");
        assertThat(outcome.kind().refuses()).isFalse();
    }

    @Test
    void aPartialLandingRefusesAndKeepsItsCause() {
        IllegalStateException cause = new IllegalStateException("push rejected");

        Outcome outcome = Outcome.partial("php reverted, java not", "half reverted", cause);

        assertThat(outcome.kind().refuses()).isTrue();
        assertThat(outcome.cause()).isSameAs(cause);
    }

    @Test
    void aGoneTaskHasNothingToStamp() {
        Outcome outcome = Outcome.gone("closed meanwhile");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.GONE);
        assertThat(outcome.stamp()).isNull();
    }
}
