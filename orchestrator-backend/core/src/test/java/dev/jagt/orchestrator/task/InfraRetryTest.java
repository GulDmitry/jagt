package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InfraRetryTest {

    @Test
    void stopsTheTaskWithTheJobAndItsReasonOnceTheLastRetryWasSent() {
        List<InfraRetry> retries = List.of(
                new InfraRetry(1_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true),
                new InfraRetry(2_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true),
                new InfraRetry(3_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true));

        Optional<TaskStop> stop = InfraRetry.stop(retries, RoundTrail.none(), 3, 4_000);

        assertThat(stop).contains(new TaskStop(TaskStop.Trigger.INFRA, 4_000,
                "unit-tests: runner_system_failure, 3 retries"));
    }

    @Test
    void leavesTheTaskWaitingWhileARetryIsLeft() {
        List<InfraRetry> retries = List.of(
                new InfraRetry(1_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true),
                InfraRetry.armed(9_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure"));

        Optional<TaskStop> stop = InfraRetry.stop(retries, RoundTrail.none(), 2, 4_000);

        assertThat(stop).isEmpty();
    }

    @Test
    void countsNoRetrySentBeforeTheLift() {
        List<InfraRetry> retries = List.of(
                new InfraRetry(1_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true),
                new InfraRetry(2_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true));

        Optional<TaskStop> stop = InfraRetry.stop(retries, RoundTrail.liftedAt(1_500), 2, 4_000);

        assertThat(stop).isEmpty();
    }

    @Test
    void stopsAtOnceWhenNoRetryIsAllowed() {
        Optional<TaskStop> stop = InfraRetry.stop(List.of(), RoundTrail.none(), 0, 4_000);

        assertThat(stop).isEmpty();
    }
}
