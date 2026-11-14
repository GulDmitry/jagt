package dev.jagt.orchestrator.port;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

class SessionLogTest {

  @Test
  void spentNothingKeepsWhereTheNextReadStarts() {
    SessionLog.Spent nothing = SessionLog.Spent.nothing(120L);

    assertThat(nothing.usage()).isEqualTo(TokenUsage.NONE);
    assertThat(nothing.upTo()).isEqualTo(120L);
  }
}
