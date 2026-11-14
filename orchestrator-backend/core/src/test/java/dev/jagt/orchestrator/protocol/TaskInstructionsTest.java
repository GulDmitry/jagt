package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskInstructionsTest {

  @Test
  void refusesRelayThatWouldEraseTheStandingInstructionWithNothing() {
    assertThat(new TaskInstructions("ABC-42", "  ").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("instructions");
  }

  @Test
  void refusesRelayNamingNoTask() {
    assertThat(new TaskInstructions(" ", "do it").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("taskId");
  }

  @Test
  void acceptsRelayNamingTaskAndSayingWhatToDo() {
    assertThat(new TaskInstructions("ABC-42", "do it").violations(MessageContext.NONE)).isEmpty();
  }
}
