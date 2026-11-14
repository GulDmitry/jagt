package dev.jagt.orchestrator.protocol.message;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Violation;
import org.junit.jupiter.api.Test;

class SessionStartTest {

  @Test
  void refusesSessionModeThisMachineDoesNotHave() {
    assertThat(new SessionStart("ABC-42", "turbo").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("mode");
  }
}
