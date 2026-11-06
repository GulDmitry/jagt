package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FactsTest {

    @Test
    void projectsEveryTaskAsHavingNoLiveAgentSoADashboardSpawnsNoProbe() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build();

        assertThat(Facts.projected(task).agentLive().getAsBoolean()).isFalse();
    }

    @Test
    void projectsATaskHoldingTwoRepositoriesAsHoldingSeveral() {
        TaskState task = TaskState.builder(List.of(TaskRepo.of("api", "/wt"), TaskRepo.of("web", "/wt2")),
                TaskStatus.IN_PROGRESS).build();

        assertThat(Facts.projected(task).severalRepos()).isTrue();
    }

    @Test
    void projectsATaskHoldingOneRepositoryAsHoldingNoSeveral() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

        assertThat(Facts.projected(task).severalRepos()).isFalse();
    }
}
