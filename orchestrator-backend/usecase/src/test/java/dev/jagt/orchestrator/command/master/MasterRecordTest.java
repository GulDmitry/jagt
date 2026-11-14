package dev.jagt.orchestrator.command.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.task.FinishedTasks;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.StatusChange;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MasterRecordTest {

  private final FinishedTasks finished = mock(FinishedTasks.class);
  private final ConfigService configService = mock(ConfigService.class);
  private final MasterRecord record = new MasterRecord(finished, configService);

  @Test
  void countsNothingWhereTheReviewerHasJudgedNothing() {
    when(finished.all())
        .thenReturn(
            List.of(
                FinishedTask.of(
                    "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).build(), 1L)));

    assertThat(record.seen().any()).isFalse();
  }

  @Test
  void readsWorkItPassedThatNobodyEverDeployedAsDisagreement() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of("proj", new ProjectConfig("/proj", "origin/main", "dev", List.of()))));
    when(finished.all())
        .thenReturn(
            List.of(
                FinishedTask.of(
                    "ABC-1",
                    TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(
                            List.of(new StatusChange(TaskStatus.CI_POLLING, 1L, ActionOrigin.MCP)))
                        .build(),
                    2L,
                    "ready")));

    assertThat(record.seen()).isEqualTo(new MasterRecord.Seen(1, 1, 0, 1, 0, 0));
  }

  @Test
  void readsWorkItFailedThatYouDeployedAnywayAsDisagreement() {
    when(finished.all())
        .thenReturn(
            List.of(
                FinishedTask.of(
                    "ABC-1",
                    TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(
                            List.of(new StatusChange(TaskStatus.DEPLOYED, 1L, ActionOrigin.BOARD)))
                        .build(),
                    2L,
                    "not ready")));

    assertThat(record.seen()).isEqualTo(new MasterRecord.Seen(1, 0, 0, 0, 0, 1));
  }

  @Test
  void readsWorkItPassedAndYouDeployedAsAgreement() {
    when(finished.all())
        .thenReturn(
            List.of(
                FinishedTask.of(
                    "ABC-1",
                    TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(
                            List.of(new StatusChange(TaskStatus.DEPLOYED, 1L, ActionOrigin.BOARD)))
                        .build(),
                    2L,
                    "ready")));

    assertThat(record.seen().disagreed()).isZero();
  }

  @Test
  void readsWorkItPassedThatYouRevertedAsDisagreement() {
    when(finished.all())
        .thenReturn(
            List.of(
                FinishedTask.of(
                    "ABC-1",
                    TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.DEPLOYED, 1L, ActionOrigin.BOARD),
                                new StatusChange(TaskStatus.REVERTED, 2L, ActionOrigin.BOARD)))
                        .build(),
                    3L,
                    "ready")));

    assertThat(record.seen()).isEqualTo(new MasterRecord.Seen(1, 1, 0, 0, 1, 0));
  }

  @Test
  void readsQuestionYouAnsweredAndDeployedAsNoDisagreement() {
    when(finished.all())
        .thenReturn(
            List.of(
                FinishedTask.of(
                    "ABC-1",
                    TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(
                            List.of(new StatusChange(TaskStatus.DEPLOYED, 1L, ActionOrigin.BOARD)))
                        .build(),
                    2L,
                    "question")));

    assertThat(record.seen()).isEqualTo(new MasterRecord.Seen(1, 0, 1, 0, 0, 0));
  }

  @Test
  void readsPassedWorkThatNeverWentOutForReviewAsNoDisagreement() {
    when(finished.all())
        .thenReturn(
            List.of(
                FinishedTask.of(
                    "ABC-1",
                    TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.REVIEW_PENDING, 1L, ActionOrigin.MCP)))
                        .build(),
                    2L,
                    "ready")));

    assertThat(record.seen().disagreed()).isZero();
  }

  @Test
  void readsPassedWorkInProjectWithNoDeployBranchAsNoDisagreement() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "secrets", new ProjectConfig("/secrets", "origin/main", null, List.of()))));
    when(finished.all())
        .thenReturn(
            List.of(
                FinishedTask.of(
                    "ABC-1",
                    TaskState.builder("secrets", "/wt", TaskStatus.DONE)
                        .history(
                            List.of(new StatusChange(TaskStatus.CI_POLLING, 1L, ActionOrigin.MCP)))
                        .build(),
                    2L,
                    "ready")));

    assertThat(record.seen().disagreed()).isZero();
  }

  @Test
  void offersTheLatestTaskYouRevertedAfterItPassed() {
    when(finished.all())
        .thenReturn(
            List.of(
                FinishedTask.of(
                    "ABC-1",
                    TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(
                            List.of(new StatusChange(TaskStatus.REVERTED, 1L, ActionOrigin.BOARD)))
                        .build(),
                    3L,
                    "ready"),
                FinishedTask.of(
                    "ABC-2",
                    TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(
                            List.of(new StatusChange(TaskStatus.REVERTED, 4L, ActionOrigin.BOARD)))
                        .build(),
                    5L,
                    "ready")));

    assertThat(record.latestDisagreement()).contains(new MasterRecord.Disagreement("ABC-2", true));
  }

  @Test
  void offersWorkItFailedThatYouDeployedAnyway() {
    when(finished.all())
        .thenReturn(
            List.of(
                FinishedTask.of(
                    "ABC-1",
                    TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(
                            List.of(new StatusChange(TaskStatus.DEPLOYED, 1L, ActionOrigin.BOARD)))
                        .build(),
                    2L,
                    "not ready")));

    assertThat(record.latestDisagreement()).contains(new MasterRecord.Disagreement("ABC-1", false));
  }

  @Test
  void offersNoLessonForPassedWorkNobodyDeployed() {
    when(finished.all())
        .thenReturn(
            List.of(
                FinishedTask.of(
                    "ABC-1",
                    TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(
                            List.of(new StatusChange(TaskStatus.CI_POLLING, 1L, ActionOrigin.MCP)))
                        .build(),
                    2L,
                    "ready")));

    assertThat(record.latestDisagreement()).isEmpty();
  }

  @Test
  void readsFailedRoundAfterTheDeployAsNoDisagreement() {
    when(finished.all())
        .thenReturn(
            List.of(
                FinishedTask.of(
                    "ABC-1",
                    TaskState.builder("proj", "/wt", TaskStatus.DONE)
                        .history(
                            List.of(
                                new StatusChange(TaskStatus.DEPLOYED, 1L, ActionOrigin.BOARD),
                                new StatusChange(TaskStatus.REVIEW_PENDING, 2L, ActionOrigin.MCP)))
                        .build(),
                    3L,
                    "not ready")));

    assertThat(record.seen().disagreed()).isZero();
  }

  @Test
  void addsEveryKindOfDisagreementIntoOneCount() {
    assertThat(new MasterRecord.Seen(9, 5, 1, 1, 2, 3).disagreed()).isEqualTo(6);
  }
}
