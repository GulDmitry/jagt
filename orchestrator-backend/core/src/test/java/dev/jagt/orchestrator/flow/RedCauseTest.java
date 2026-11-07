package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.FailedJob;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedCauseTest {

  @Test
  void aRunFailedOnInfrastructureWhenEveryJobDiedOnTheRunnerOrTheHost() {
    RedCause cause =
        RedCause.of(
            List.of(
                new FailedJob("1", "build", "runner_lost", true),
                new FailedJob("2", "unit-tests", "host_gone", true)));

    assertThat(cause).isEqualTo(RedCause.INFRASTRUCTURE);
  }

  @Test
  void aRunFailedForTheCodeWhenOneJobDiedOnInfrastructureAndAnotherOnItsScript() {
    RedCause cause =
        RedCause.of(
            List.of(
                new FailedJob("1", "build", "runner_lost", true),
                new FailedJob("2", "unit-tests", "script_failure", false)));

    assertThat(cause).isEqualTo(RedCause.CODE);
  }

  @Test
  void aRunFailedForTheCodeWhenTheHostGivesNoReasonForAJob() {
    RedCause cause = RedCause.of(List.of(new FailedJob("1", "unit-tests", "", false)));

    assertThat(cause).isEqualTo(RedCause.CODE);
  }

  @Test
  void aRunFailedForTheCodeWhenTheHostListsNoFailedJob() {
    RedCause cause = RedCause.of(List.of());

    assertThat(cause).isEqualTo(RedCause.CODE);
  }
}
