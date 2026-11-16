package dev.jagt.orchestrator.surface.board.auth.attempts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginAttemptsTest {

  private final Clock clock = mock(Clock.class);
  private final LoginAttempts attempts =
      new LoginAttempts(new LoginBackoff(3, Duration.ofMinutes(5), 2), clock);

  @Test
  void refusesPeerForTheBackoffOnceItFailedTheLimit() {
    when(clock.millis()).thenReturn(1_000L);

    attempts.failed("10.0.0.1");
    attempts.failed("10.0.0.1");
    attempts.failed("10.0.0.1");

    assertThat(attempts.remaining("10.0.0.1")).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void letsPeerTryBelowTheLimit() {
    when(clock.millis()).thenReturn(1_000L);

    attempts.failed("10.0.0.1");
    attempts.failed("10.0.0.1");

    assertThat(attempts.remaining("10.0.0.1")).isZero();
  }

  @Test
  void leavesAnotherPeerUntouched() {
    when(clock.millis()).thenReturn(1_000L);

    attempts.failed("10.0.0.1");
    attempts.failed("10.0.0.1");
    attempts.failed("10.0.0.1");

    assertThat(attempts.remaining("10.0.0.2")).isZero();
  }

  @Test
  void forgetsFailuresWhenThePeerLogsIn() {
    when(clock.millis()).thenReturn(1_000L);
    attempts.failed("10.0.0.1");
    attempts.failed("10.0.0.1");

    attempts.succeeded("10.0.0.1");
    attempts.failed("10.0.0.1");
    attempts.failed("10.0.0.1");

    assertThat(attempts.remaining("10.0.0.1")).isZero();
  }

  @Test
  void letsPeerTryAgainWhenTheBackoffHasPassed() {
    when(clock.millis()).thenReturn(1_000L);
    attempts.failed("10.0.0.1");
    attempts.failed("10.0.0.1");
    attempts.failed("10.0.0.1");

    when(clock.millis()).thenReturn(1_000L + 300_000L);

    assertThat(attempts.remaining("10.0.0.1")).isZero();
  }

  @Test
  void countsFromOneAgainWhenTheLastFailureIsOlderThanTheBackoff() {
    when(clock.millis()).thenReturn(1_000L);
    attempts.failed("10.0.0.1");
    attempts.failed("10.0.0.1");

    when(clock.millis()).thenReturn(1_000L + 300_000L);
    attempts.failed("10.0.0.1");

    assertThat(attempts.remaining("10.0.0.1")).isZero();
  }

  @Test
  void dropsTheLeastRecentlySeenPeerPastTheCap() {
    when(clock.millis()).thenReturn(1_000L);
    attempts.failed("10.0.0.1");
    attempts.failed("10.0.0.1");
    attempts.failed("10.0.0.1");

    attempts.failed("10.0.0.2");
    attempts.failed("10.0.0.3");

    assertThat(attempts.remaining("10.0.0.1")).isZero();
  }
}
