package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskRefTest {

  @Test
  void refusesVerbWithNoTaskToActOn() {
    assertThat(new TaskRef("").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("taskId");
  }

  @Test
  void readsBlankTaskAsNoTask() {
    assertThat(new TaskRef("  ").taskId()).isNull();
    assertThat(new TaskRef("ABC-42").violations(MessageContext.NONE)).isEmpty();
  }
}
