package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class FactsTest {

  @Test
  void projectsEveryTaskAsHavingNoLiveAgentSoADashboardSpawnsNoProbe() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build();

    assertThat(Facts.projected(task).hold().agentLive().getAsBoolean()).isFalse();
  }

  @Test
  void projectsATaskHoldingTwoRepositoriesAsHoldingSeveral() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/wt"), TaskRepo.of("web", "/wt2")),
                TaskStatus.IN_PROGRESS)
            .build();

    assertThat(Facts.projected(task).workspace().severalRepos()).isTrue();
  }

  @Test
  void projectsATaskHoldingOneRepositoryAsHoldingNoSeveral() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

    assertThat(Facts.projected(task).workspace().severalRepos()).isFalse();
  }
}
