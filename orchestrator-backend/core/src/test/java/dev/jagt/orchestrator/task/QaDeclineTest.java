package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QaDeclineTest {

  @Test
  void failsWithTheFailureAndNoDispute() {
    assertThat(QaDecline.failed("abc", "spend ceiling"))
        .isEqualTo(new QaDecline("abc", "spend ceiling", ""));
  }
}
