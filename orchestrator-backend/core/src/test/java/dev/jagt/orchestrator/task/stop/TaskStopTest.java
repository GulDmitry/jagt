package dev.jagt.orchestrator.task.stop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskStopTest {

  @Test
  void unchangedFailureNextNamesProbeExecingIntoStackNoProbeStarts() {
    String next = TaskStop.Trigger.UNCHANGED_FAILURE.next();

    assertThat(next).contains("probe that execs into a stack no earlier probe starts");
  }
}
