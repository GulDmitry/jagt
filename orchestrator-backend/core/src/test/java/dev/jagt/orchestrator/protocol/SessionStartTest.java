package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionStartTest {

  @Test
  void refusesSessionModeThisMachineDoesNotHave() {
    assertThat(new SessionStart("ABC-42", "turbo").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("mode");
  }
}
