package dev.jagt.orchestrator.task.stop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaskStopTest {

  @ParameterizedTest
  @CsvSource({
    "PAUSE,PAUSE,PAUSE",
    "STALLED,GUARD,LIFT",
    "SHIPS,GUARD,LIFT",
    "MISS,GUARD,LIFT",
    "ROUNDS,GUARD,LIFT",
    "PACE,GUARD,LIFT",
    "UNCHANGED_FAILURE,GUARD,LIFT",
    "ACCEPTANCE_FAILED,GUARD,LIFT",
    "CHECKS,BROKEN,HOST_READ",
    "INFRA,BROKEN,HOST_READ",
    "UNDELIVERED,BROKEN,SESSION_ALIVE",
    "WAIT,BROKEN,SESSION_REPORT",
    "DEPLOY_UNREAD,BROKEN,DEPLOY_READ"
  })
  void sortsEveryTriggerIntoGuardExactlyWhereOnlyLiftEndsIt(
      TaskStop.Trigger trigger, TaskStop.Trigger.Kind kind, TaskStop.Trigger.Ends ends) {
    assertThat(trigger.kind()).isEqualTo(kind);
    assertThat(trigger.ends()).isEqualTo(ends);
  }

  @ParameterizedTest
  @CsvSource({"CHECKS", "INFRA", "UNDELIVERED", "WAIT", "DEPLOY_UNREAD"})
  void neverTellsTheHumanToLiftWhereReadEndsTheStop(TaskStop.Trigger trigger) {
    assertThat(trigger.next()).doesNotContain("lift");
    assertThat(new TaskStop(trigger, 1L, "evidence").reading()).doesNotContain("lift");
  }

  @Test
  void readingTellsTheHumanToLiftOrCloseWhereOnlyLiftEndsTheStop() {
    TaskStop stop = new TaskStop(TaskStop.Trigger.SHIPS, 1L, "evidence");

    assertThat(stop.reading()).endsWith(", then lift or close");
  }

  @Test
  void unchangedFailureNextNamesProbeExecingIntoStackNoProbeStarts() {
    String next = TaskStop.Trigger.UNCHANGED_FAILURE.next();

    assertThat(next).contains("probe that execs into a stack no earlier probe starts");
  }
}
