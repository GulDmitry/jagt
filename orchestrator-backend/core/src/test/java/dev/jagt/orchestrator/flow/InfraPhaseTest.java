package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.InfraRetry;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InfraPhaseTest {

    @Test
    void isSpentOnceTheLastRetryOfTheBudgetWasSent() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.CI_POLLING).build().withInfraRetries(List.of(
                new InfraRetry(1_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true, 3, 3)));

        assertThat(InfraPhase.of(task)).isEqualTo(InfraPhase.SPENT);
    }
}
