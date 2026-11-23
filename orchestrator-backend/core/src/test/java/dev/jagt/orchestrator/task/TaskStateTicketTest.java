package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskStateTicketTest {

  @Test
  void takesTheProjectDefaultBaseBranchOnlyWhereTheTaskNamedNone() {
    TaskState own = TaskState.builder("proj", "/wt", TaskStatus.NEW).baseBranch("release").build();
    TaskState blank = TaskState.builder("proj", "/wt", TaskStatus.NEW).baseBranch(" ").build();
    TaskState none = TaskState.builder("proj", "/wt", TaskStatus.NEW).build();

    assertThat(own.ticket().baseBranchOr("main")).isEqualTo("release");
    assertThat(blank.ticket().baseBranchOr("main")).isEqualTo("main");
    assertThat(none.ticket().baseBranchOr("main")).isEqualTo("main");
  }
}
