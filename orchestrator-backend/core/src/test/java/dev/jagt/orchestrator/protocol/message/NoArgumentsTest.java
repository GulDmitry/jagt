package dev.jagt.orchestrator.protocol.message;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.protocol.MessageContext;
import org.junit.jupiter.api.Test;

class NoArgumentsTest {

  @Test
  void asksNothingOfToolThatAsksForNothing() {
    assertThat(new NoArguments().violations(MessageContext.NONE)).isEmpty();
  }
}
