package dev.jagt.orchestrator.task.stop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GuardTest {

  @Test
  void readsAsWordsWithoutUnderscoresWhereRefusalIsCounted() {
    assertThat(Guard.GIT_STATE.label()).isEqualTo("git state");
    assertThat(Guard.STATUS_REPORT.label()).isEqualTo("status report");
  }
}
