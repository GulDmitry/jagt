package dev.jagt.orchestrator.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.session.Standing;
import dev.jagt.orchestrator.service.git.TrustedGitState;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.Step;
import org.junit.jupiter.api.Test;

class TrustCapabilityTest {

  private final TrustedGitState trusted = mock(TrustedGitState.class);
  private final StepRelay relay = mock(StepRelay.class);

  @Test
  void trustsTheGitStateOfTheTaskAsItStandsAndSaysSo() {
    Outcome outcome = new TrustCapability(trusted, relay).run("ABC-1");

    verify(trusted).trust("ABC-1");
    assertThat(outcome.message())
        .isEqualTo("trust ABC-1: its git config and hooks are trusted as they are now");
  }

  @Test
  void tellsTheSessionToGoOnWhereItWaitedOnTheTrust() {
    Standing read = new Standing(TaskStatus.IN_PROGRESS, "", 0, 0);
    when(trusted.trust("ABC-1")).thenReturn(true);
    when(relay.standing("ABC-1")).thenReturn(read);
    when(relay.relay("ABC-1", Step.ANSWERED, TrustCapability.TRUSTED, read)).thenReturn(true);

    Outcome outcome = new TrustCapability(trusted, relay).run("ABC-1");

    assertThat(outcome.message()).endsWith("; its session is told to go on");
  }

  @Test
  void saysNothingOfTheSessionWhereTheRelayWasRefused() {
    Standing read = new Standing(TaskStatus.IN_PROGRESS, "", 0, 0);
    when(trusted.trust("ABC-1")).thenReturn(true);
    when(relay.standing("ABC-1")).thenReturn(read);

    Outcome outcome = new TrustCapability(trusted, relay).run("ABC-1");

    assertThat(outcome.message())
        .isEqualTo("trust ABC-1: its git config and hooks are trusted as they are now");
  }

  @Test
  void tellsNoSessionWhereNoneWaitedOnTheTrust() {
    new TrustCapability(trusted, relay).run("ABC-1");

    verifyNoInteractions(relay);
  }
}
