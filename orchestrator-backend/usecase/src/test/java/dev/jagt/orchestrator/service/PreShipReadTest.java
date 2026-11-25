package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.AgentReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PreShipReadTest {

  @Test
  void isDueWhenTaskAwaitsReviewUnstoppedUnaskingWithWorktree() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

    assertThat(PreShipRead.due(task)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"IN_PROGRESS", "CI_POLLING", "SHIPPING"})
  void isNotDueWhenTaskIsNotAwaitingReview(TaskStatus status) {
    TaskState task = TaskState.builder("proj", "/wt", status).build();

    assertThat(PreShipRead.due(task)).isFalse();
  }

  @Test
  void isNotDueWhenTaskIsStopped() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .stop(new TaskStop(TaskStop.Trigger.STALLED, 7L, "same round"))
            .build();

    assertThat(PreShipRead.due(task)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = AgentReport.class,
      names = {"QUESTION", "PERMISSION"})
  void isNotDueWhenAgentReportAsks(AgentReport report) {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).report(report).build();

    assertThat(PreShipRead.due(task)).isFalse();
  }

  @Test
  void isNotDueWhenTaskHasNoWorktree() {
    TaskState task = TaskState.builder("proj", null, TaskStatus.REVIEW_PENDING).build();

    assertThat(PreShipRead.due(task)).isFalse();
  }
}
