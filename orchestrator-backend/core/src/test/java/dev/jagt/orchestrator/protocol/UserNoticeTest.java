package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserNoticeTest {

  @Test
  void takesBlankTitleAsNoneAndKeepsGivenOne() {
    assertThat(new UserNotice(" ", "done").title()).isNull();
    assertThat(new UserNotice("jagt", "done").title()).isEqualTo("jagt");
  }

  @Test
  void refusesBannerWithNoLineInIt() {
    assertThat(new UserNotice("jagt", "").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("message");
  }

  @Test
  void refusesBannerWhoseLineIsBlank() {
    assertThat(new UserNotice("jagt", "  ").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("message");
  }

  @Test
  void acceptsBannerWithLineAndReadsBlankTitleAsLeftOut() {
    UserNotice notice = new UserNotice("  ", "review round addressed");

    assertThat(notice.violations(MessageContext.NONE)).isEmpty();
    assertThat(notice.title()).isNull();
  }
}
