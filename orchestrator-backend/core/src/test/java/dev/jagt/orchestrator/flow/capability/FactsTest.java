package dev.jagt.orchestrator.flow.capability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.ChecksUnread;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.usage.ModelTier;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class FactsTest {

  @Test
  void projectsEveryTaskAsHavingNoLiveAgentSoDashboardSpawnsNoProbe() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build();

    assertThat(Facts.projected(task).hold().agentLive().getAsBoolean()).isFalse();
  }

  @Test
  void projectsTaskHoldingTwoRepositoriesAsHoldingSeveral() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/wt"), TaskRepo.of("web", "/wt2")),
                TaskStatus.IN_PROGRESS)
            .build();

    assertThat(Facts.projected(task).workspace().severalRepos()).isTrue();
  }

  @Test
  void projectsTaskHoldingOneRepositoryAsHoldingNoSeveral() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

    assertThat(Facts.projected(task).workspace().severalRepos()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStop.Trigger.class,
      names = {"STALLED", "SHIPS", "ROUNDS", "UNCHANGED_FAILURE", "ACCEPTANCE_FAILED"})
  void projectsTierAboveOnStopThatCountsAsFailureWhenTheLadderHasOne(TaskStop.Trigger trigger) {
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
  void projectsNoTierAboveOnStopThatIsNoFailureOfTheModel(TaskStop.Trigger trigger) {
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
  void projectsNoTierAboveWithoutStop() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withTier(new ModelTier(List.of("sonnet", "opus"), 0));

    assertThat(Facts.projected(task).hold().tierAbove()).isFalse();
  }

  @Test
  void projectsNoTierAboveForSessionWithNoLadder() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.STALLED, 1_000, "rounds"))
            .build();

    assertThat(Facts.projected(task).hold().tierAbove()).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "success,b2,b2,true",
    "running,b2,b2,false",
    "success,a1,b2,false",
    "none,b2,b2,true"
  })
  void projectsChecksGreenOnlyWhereTheHeadsChecksReadGreenOrListNone(
      String pipeline, String checksTip, String headTip, boolean green) {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
            .pipelineStatus(pipeline)
            .checksTip(checksTip)
            .headTip(headTip)
            .build();

    assertThat(Facts.projected(task).delivery().checksGreen()).isEqualTo(green);
  }

  @Test
  void projectsNoChecksGreenOnceTheReadAfterTheGreenFailed() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
            .pipelineStatus("success")
            .checksTip("b2")
            .headTip("b2")
            .checksUnread(List.of(new ChecksUnread(0, "down")))
            .build();

    assertThat(Facts.projected(task).delivery().checksGreen()).isFalse();
  }
}
