package dev.jagt.orchestrator.task.stop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GuardKindTest {

  @Test
  void readsAsWordsWithoutUnderscoresWhereRefusalIsCounted() {
    assertThat(GuardKind.GIT_STATE.label()).isEqualTo("git state");
    assertThat(GuardKind.STATUS_REPORT.label()).isEqualTo("status report");
  }
}
