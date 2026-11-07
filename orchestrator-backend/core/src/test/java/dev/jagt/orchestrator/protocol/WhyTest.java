package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WhyTest {

  @Test
  void acceptsAReasonOfExactlyTheLongestAllowedLength() {
    assertThat(Why.violation("x".repeat(120))).isEmpty();
  }

  @Test
  void refusesAReasonOneCharacterPastTheLimit() {
    assertThat(Why.violation("x".repeat(121))).isPresent();
  }

  @Test
  void refusesAMissingReason() {
    assertThat(Why.violation(null)).isPresent();
  }
}
