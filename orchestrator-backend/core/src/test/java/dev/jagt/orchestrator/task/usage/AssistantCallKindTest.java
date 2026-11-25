package dev.jagt.orchestrator.task.usage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AssistantCallKindTest {

  @ParameterizedTest
  @EnumSource(
      value = AssistantCallKind.class,
      names = {"COMMAND_MAP", "BRANCH_NAME"})
  void readsNoToolForKindsAnsweredFromTheirPromptAlone(AssistantCallKind kind) {
    assertThat(kind.readsTools()).isFalse();
  }

  @Test
  void readsToolsForTicketRead() {
    assertThat(AssistantCallKind.TICKET_READ.readsTools()).isTrue();
  }
}
