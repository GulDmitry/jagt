package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AcceptanceDeclineTest {

  @Test
  void failsWithTheFailureAndNoDispute() {
    assertThat(AcceptanceDecline.failed("abc", "spend ceiling"))
        .isEqualTo(new AcceptanceDecline("abc", "spend ceiling", ""));
  }
}
