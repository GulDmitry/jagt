package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QaDeclineTest {

    @Test
    void failsWithTheFailureAndNoDispute() {
        assertThat(QaDecline.failed("abc", "spend ceiling")).isEqualTo(new QaDecline("abc", "spend ceiling", ""));
    }
}
