package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.ModelTier;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

  @ParameterizedTest
  @EnumSource(
      value = TaskStop.Trigger.class,
      names = {"STALLED", "SHIPS", "ROUNDS", "UNCHANGED_FAILURE", "QA_FAILED"})
  void projectsATierAboveOnAStopThatCountsAsAFailureWhenTheLadderHasOne(
      TaskStop.Trigger trigger) {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(trigger, 1_000, "same failure"))
            .build()
            .withTier(new ModelTier(List.of("sonnet", "opus"), 0));

    assertThat(Facts.projected(task).hold().tierAbove()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStop.Trigger.class,
      names = {"PAUSE", "CHECKS", "INFRA", "PACE", "MISS"})
  void projectsNoTierAboveOnAStopThatIsNoFailureOfTheModel(TaskStop.Trigger trigger) {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(trigger, 1_000, "waiting"))
            .build()
            .withTier(new ModelTier(List.of("sonnet", "opus"), 0));

    assertThat(Facts.projected(task).hold().tierAbove()).isFalse();
  }

  @Test
  void projectsNoTierAboveOnTheLastTier() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.STALLED, 1_000, "rounds"))
            .build()
            .withTier(new ModelTier(List.of("sonnet", "opus"), 1));

    assertThat(Facts.projected(task).hold().tierAbove()).isFalse();
  }

  @Test
  void projectsNoTierAboveWithoutAStop() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withTier(new ModelTier(List.of("sonnet", "opus"), 0));

    assertThat(Facts.projected(task).hold().tierAbove()).isFalse();
  }

  @Test
  void projectsNoTierAboveForASessionWithNoLadder() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.STALLED, 1_000, "rounds"))
            .build();

    assertThat(Facts.projected(task).hold().tierAbove()).isFalse();
  }
}
