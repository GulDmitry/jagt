package dev.jagt.orchestrator.surface.board.auth.attempts;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.testing.FakeClock;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

class PasswordLoginOutcomeTest {

  private final LoginAttempts attempts =
      new LoginAttempts(new LoginBackoff(1, Duration.ofMinutes(5), 10), new FakeClock());
  private final PasswordLoginOutcome outcome = new PasswordLoginOutcome(attempts, new LoginAudit());

  @Test
  void saysOneWarnLineWithPeerModeAndCauseWhenThePasswordIsWrong() {
    try (FakeLogCapture log = new FakeLogCapture(LoginAudit.class)) {
      outcome.failed("10.0.0.1", new BadCredentialsException("Bad credentials"));

      assertThat(log.events()).hasSize(1);
      ILoggingEvent line = log.events().getFirst();
      assertThat(line.getLevel()).isEqualTo(Level.WARN);
      assertThat(line.getMessage()).isEqualTo("login failed");
      assertThat(line.getKeyValuePairs())
          .extracting(pair -> pair.key + "=" + pair.value)
          .containsExactly("peer=10.0.0.1", "mode=password", "cause=Bad credentials");
    }
  }

  @Test
  void saysOneInfoLineWithPeerAndModeWhenThePasswordIsRight() {
    try (FakeLogCapture log = new FakeLogCapture(LoginAudit.class)) {
      outcome.succeeded("10.0.0.1");

      assertThat(log.events()).hasSize(1);
      ILoggingEvent line = log.events().getFirst();
      assertThat(line.getLevel()).isEqualTo(Level.INFO);
      assertThat(line.getMessage()).isEqualTo("login succeeded");
      assertThat(line.getKeyValuePairs())
          .extracting(pair -> pair.key + "=" + pair.value)
          .containsExactly("peer=10.0.0.1", "mode=password");
    }
  }

  @Test
  void backsThePeerOffWhenItsPasswordIsWrongPastTheLimit() {
    outcome.failed("10.0.0.1", new BadCredentialsException("Bad credentials"));

    assertThat(attempts.remaining("10.0.0.1")).isPositive();
  }

  @Test
  void clearsThePeersFailuresWhenItsPasswordIsRight() {
    outcome.failed("10.0.0.1", new BadCredentialsException("Bad credentials"));

    outcome.succeeded("10.0.0.1");

    assertThat(attempts.remaining("10.0.0.1")).isZero();
  }
}
