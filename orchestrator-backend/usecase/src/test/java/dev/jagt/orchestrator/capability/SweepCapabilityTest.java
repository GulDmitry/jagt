package dev.jagt.orchestrator.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.service.review.ReviewSweepService;
import dev.jagt.orchestrator.service.review.ReviewSweepService.SweepResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SweepCapabilityTest {

  private final ReviewSweepService sweeps = mock(ReviewSweepService.class);

  @ParameterizedTest
  @EnumSource(
      value = SweepResult.Kind.class,
      names = {"UNCHANGED", "PENDING"})
  void answersAsNothingDoneWhenTheRoundHasNothingNew(SweepResult.Kind kind) {
    when(sweeps.sweep("ABC-1")).thenReturn(new SweepResult(kind, "sweep ABC-1: same"));

    Outcome outcome = new SweepCapability(sweeps).run("ABC-1");

    assertThat(outcome.kind()).isEqualTo(Outcome.Kind.NOTHING);
    assertThat(outcome.message()).isEqualTo("sweep ABC-1: same");
  }

  @Test
  void answersAsDoneWhenTheRoundWasRelayed() {
    when(sweeps.sweep("ABC-1"))
        .thenReturn(new SweepResult(SweepResult.Kind.RELAYED, "sweep ABC-1: relayed"));

    Outcome outcome = new SweepCapability(sweeps).run("ABC-1");

    assertThat(outcome.kind()).isEqualTo(Outcome.Kind.OK);
  }
}
