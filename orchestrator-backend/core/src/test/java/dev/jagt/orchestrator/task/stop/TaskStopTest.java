package dev.jagt.orchestrator.task.stop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaskStopTest {

  @ParameterizedTest
  @CsvSource({
    "PAUSE,PAUSE",
    "STALLED,GUARD",
    "SHIPS,GUARD",
    "MISS,GUARD",
    "ROUNDS,GUARD",
    "PACE,GUARD",
    "UNCHANGED_FAILURE,GUARD",
    "CHECKS,BROKEN",
    "INFRA,BROKEN",
    "UNDELIVERED,BROKEN",
    "WAIT,BROKEN",
    "DEPLOY_UNREAD,BROKEN",
    "ACCEPTANCE_FAILED,BROKEN"
  })
  void sortsEveryTriggerIntoWhoseStopItIs(TaskStop.Trigger trigger, TaskStop.Trigger.Kind kind) {
    assertThat(trigger.kind()).isEqualTo(kind);
  }

  @Test
  void unchangedFailureNextNamesProbeExecingIntoStackNoProbeStarts() {
    String next = TaskStop.Trigger.UNCHANGED_FAILURE.next();

    assertThat(next).contains("probe that execs into a stack no earlier probe starts");
  }
}
