package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoArgumentsTest {

  @Test
  void asksNothingOfToolThatAsksForNothing() {
    assertThat(new NoArguments().violations(MessageContext.NONE)).isEmpty();
  }
}
