package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.FailedJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedCauseTest {

    @ParameterizedTest
    @ValueSource(strings = {"runner_system_failure", "stuck_or_timeout_failure", "api_failure", "scheduler_failure",
            "data_integrity_failure", "startup_failure"})
    void aRunFailedOnInfrastructureWhenEveryJobDiedOnTheRunnerOrTheHost(String reason) {
        RedCause cause = RedCause.of(List.of(new FailedJob("1", "unit-tests", reason)));

        assertThat(cause).isEqualTo(RedCause.INFRASTRUCTURE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"script_failure", "unknown_failure", ""})
    void aRunFailedForTheCodeWhenTheHostGivesAnyOtherReasonOrNone(String reason) {
        RedCause cause = RedCause.of(List.of(new FailedJob("1", "unit-tests", reason)));

        assertThat(cause).isEqualTo(RedCause.CODE);
    }

    @Test
    void aRunFailedForTheCodeWhenOneJobDiedOnInfrastructureAndAnotherOnItsScript() {
        RedCause cause = RedCause.of(List.of(new FailedJob("1", "build", "runner_system_failure"),
                new FailedJob("2", "unit-tests", "script_failure")));

        assertThat(cause).isEqualTo(RedCause.CODE);
    }

    @Test
    void aRunFailedForTheCodeWhenTheHostListsNoFailedJob() {
        RedCause cause = RedCause.of(List.of());

        assertThat(cause).isEqualTo(RedCause.CODE);
    }
}
