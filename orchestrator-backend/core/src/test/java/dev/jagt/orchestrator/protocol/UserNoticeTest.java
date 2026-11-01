package dev.jagt.orchestrator.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserNoticeTest {

    @Test
    void takesABlankTitleAsNoneAndKeepsAGivenOne() {
        assertThat(new UserNotice(" ", "done").title()).isNull();
        assertThat(new UserNotice("jagt", "done").title()).isEqualTo("jagt");
    }
}
