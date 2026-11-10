package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CausesTest {

  @Test
  void failureWithBlankMessageIsNamedByItsType() {
    String cause = Causes.of(new IllegalStateException(" "));

    assertThat(cause).isEqualTo("IllegalStateException");
  }
}
