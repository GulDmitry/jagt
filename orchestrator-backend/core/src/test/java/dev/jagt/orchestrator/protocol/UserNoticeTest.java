package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserNoticeTest {

  @Test
  void takesABlankTitleAsNoneAndKeepsAGivenOne() {
    assertThat(new UserNotice(" ", "done").title()).isNull();
    assertThat(new UserNotice("jagt", "done").title()).isEqualTo("jagt");
  }
}
