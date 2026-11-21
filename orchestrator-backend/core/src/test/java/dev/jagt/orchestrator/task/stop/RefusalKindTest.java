package dev.jagt.orchestrator.task.stop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefusalKindTest {

  @Test
  void readsAsWordsWithoutUnderscoresWhereRefusalIsCounted() {
    assertThat(RefusalKind.GIT_STATE.label()).isEqualTo("git state");
    assertThat(RefusalKind.STATUS_REPORT.label()).isEqualTo("status report");
  }
}
