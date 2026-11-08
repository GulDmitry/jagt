package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelTierTest {

  @Test
  void laddersFromTheStartModelUpThroughTheTiersListedAfterIt() {
    ModelTier tier = ModelTier.above("sonnet", List.of("haiku", "sonnet", "opus", "fable"));

    assertThat(tier.models()).containsExactly("sonnet", "opus", "fable");
    assertThat(tier.rung()).isZero();
  }

  @Test
  void takesTheFirstTierForAStartNoTierNames() {
    ModelTier tier = ModelTier.above("", List.of("sonnet", "opus"));

    assertThat(tier.models()).containsExactly("", "opus");
  }

  @Test
  void hasNoLadderWhenTheStartModelIsTheLastTier() {
    assertThat(ModelTier.above("opus", List.of("sonnet", "opus"))).isNull();
  }

  @Test
  void hasNoLadderWhenNoTierIsConfigured() {
    assertThat(ModelTier.above("sonnet", List.of())).isNull();
  }

  @Test
  void raisesOneRungAndHasNoneAboveTheLast() {
    ModelTier raised = ModelTier.above("sonnet", List.of("sonnet", "opus")).raise();

    assertThat(raised.current()).isEqualTo("opus");
    assertThat(raised.raised()).isTrue();
    assertThat(raised.higher()).isFalse();
  }

  @Test
  void lowersBackToTheModelItStartedOn() {
    ModelTier lowered = ModelTier.above("sonnet", List.of("sonnet", "opus")).raise().lower();

    assertThat(lowered.current()).isEqualTo("sonnet");
    assertThat(lowered.raised()).isFalse();
  }
}
