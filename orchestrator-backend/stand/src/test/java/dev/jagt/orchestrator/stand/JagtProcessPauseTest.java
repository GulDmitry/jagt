package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JagtProcessPauseTest {

  @Test
  void freePortIsHighLoopbackPort() {
    assertThat(JagtProcess.freePort()).isGreaterThan(1023);
  }

  @Test
  void pauseAnInterruptedThreadCannotKeepIsFailureThatKeepsTheInterrupt() {
    Thread.currentThread().interrupt();

    assertThatThrownBy(() -> JagtProcess.pause(Duration.ofMillis(1)))
        .isInstanceOf(StandFailure.class);
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test
  void shortPauseReturns() {
    JagtProcess.pause(Duration.ZERO);

    assertThat(Thread.currentThread().isInterrupted()).isFalse();
  }
}
