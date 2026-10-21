package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FactsTest {

    @Test
    void projectsEveryTaskAsHavingNoLiveAgentSoADashboardSpawnsNoProbe() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build();

        assertThat(Facts.projected(task).agentLive().getAsBoolean()).isFalse();
    }
}
