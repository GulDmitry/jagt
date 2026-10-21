package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuardTest {

    @Test
    void readsAsWordsWithoutUnderscoresWhereARefusalIsCounted() {
        assertThat(Guard.GIT_STATE.label()).isEqualTo("git state");
        assertThat(Guard.STATUS_REPORT.label()).isEqualTo("status report");
    }
}
