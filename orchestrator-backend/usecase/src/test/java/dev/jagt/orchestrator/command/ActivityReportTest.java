package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.FinishedTasks;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ActivityReportTest {

  private final StateService state = mock(StateService.class);
  private final FinishedTasks finished = mock(FinishedTasks.class);
  private final ActivityReport report = new ActivityReport(state, finished);

  @Test
  void listsATasksStatusChangeWithItsTimeInUtcItsStatusesAndItsOrigin() {
    when(finished.all()).thenReturn(List.of());
    when(state.tasks())
        .thenReturn(
            Map.of(
                "ABC-42",
                TaskState.builder("alpha", "/wt", TaskStatus.REVIEW_PENDING)
                    .history(
                        List.of(
                            new StatusChange(TaskStatus.IN_PROGRESS, 1_786_000_000_000L, null),
                            new StatusChange(
                                TaskStatus.REVIEW_PENDING,
                                1_786_000_060_000L,
                                ActionOrigin.MCP)))
                    .build()));

    String said = report.render("");

    assertThat(said)
        .contains("2026-08-06 07:07:40  ABC-42         agent working → not shipped  mcp");
  }

  @Test
  void showsTheNewestChangeFirstAcrossLiveAndFinishedTasks() {
    TaskState retired =
        TaskState.builder("alpha", "/wt", TaskStatus.DONE)
            .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 1_000L, null)))
            .build();
    when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-1", retired, 3_000L)));
    when(state.tasks())
        .thenReturn(
            Map.of(
                "ABC-2",
                TaskState.builder("alpha", "/wt", TaskStatus.IN_PROGRESS)
                    .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 2_000L, null)))
                    .build()));

    String said = report.render("");

    assertThat(said).containsSubsequence("ABC-1", "ABC-2", "ABC-1");
  }

  @Test
  void endsAFinishedTasksLinesWithItsRetirementAsDoneWithNoOrigin() {
    TaskState retired =
        TaskState.builder("alpha", "/wt", TaskStatus.REVIEW_PENDING)
            .history(List.of(new StatusChange(TaskStatus.REVIEW_PENDING, 1_000L, null)))
            .build();
    when(state.tasks()).thenReturn(Map.of());
    when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-1", retired, 60_000L)));

    String said = report.render("");

    assertThat(said).contains("1970-01-01 00:01:00  ABC-1          not shipped → done  -");
  }

  @Test
  void addsNoSecondDoneLineWhenTheHistoryAlreadyEndsInDone() {
    TaskState retired =
        TaskState.builder("alpha", "/wt", TaskStatus.DONE)
            .history(
                List.of(
                    new StatusChange(TaskStatus.IN_PROGRESS, 1_000L, null),
                    new StatusChange(TaskStatus.DONE, 2_000L, ActionOrigin.BOARD)))
            .build();
    when(state.tasks()).thenReturn(Map.of());
    when(finished.all()).thenReturn(List.of(FinishedTask.of("ABC-1", retired, 3_000L)));

    String said = report.render("done");

    assertThat(said.lines().filter(line -> line.contains("→ done")).count()).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({
    "created, starting",
    "shipped, pushing",
    "stopped, checks failed",
    "stopped, deploy conflict",
    "deployed, deployed",
    "done, done"
  })
  void keepsOnlyTheChangesOfTheKindAsked(String kind, String landedOn) {
    when(finished.all()).thenReturn(List.of());
    when(state.tasks())
        .thenReturn(
            Map.of(
                "ABC-42",
                TaskState.builder("alpha", "/wt", TaskStatus.DONE)
                    .history(
                        List.of(
                            new StatusChange(TaskStatus.NEW, 1_000L, null),
                            new StatusChange(TaskStatus.IN_PROGRESS, 2_000L, null),
                            new StatusChange(TaskStatus.SHIPPING, 3_000L, null),
                            new StatusChange(TaskStatus.CI_FAILED, 4_000L, null),
                            new StatusChange(TaskStatus.DEPLOY_CONFLICT, 5_000L, null),
                            new StatusChange(TaskStatus.DEPLOYED, 6_000L, null),
                            new StatusChange(TaskStatus.DONE, 7_000L, null)))
                    .build()));

    String said = report.render(kind);

    assertThat(said).contains("→ " + landedOn).doesNotContain("→ agent working");
  }

  @Test
  void keepsOnlyTheChangesOfTheProjectAsked() {
    when(finished.all()).thenReturn(List.of());
    when(state.tasks())
        .thenReturn(
            Map.of(
                "ABC-1",
                TaskState.builder("alpha", "/wt", TaskStatus.IN_PROGRESS)
                    .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 1_000L, null)))
                    .build(),
                "ABC-2",
                TaskState.builder("beta", "/wt", TaskStatus.IN_PROGRESS)
                    .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 2_000L, null)))
                    .build()));

    String said = report.render("beta");

    assertThat(said).contains("ABC-2").doesNotContain("ABC-1");
  }

  @Test
  void combinesAKindAndAProjectInEitherOrder() {
    when(finished.all()).thenReturn(List.of());
    when(state.tasks())
        .thenReturn(
            Map.of(
                "ABC-1",
                TaskState.builder("alpha", "/wt", TaskStatus.DEPLOYED)
                    .history(
                        List.of(
                            new StatusChange(TaskStatus.IN_PROGRESS, 1_000L, null),
                            new StatusChange(TaskStatus.DEPLOYED, 2_000L, null)))
                    .build()));

    String said = report.render("alpha deployed");

    assertThat(said).contains("→ deployed").doesNotContain("→ agent working");
  }

  @Test
  void saysNothingMatchesNamingTheFilterWhenNoChangeDoes() {
    when(finished.all()).thenReturn(List.of());
    when(state.tasks()).thenReturn(Map.of());

    String said = report.render("shipped alpha");

    assertThat(said).isEqualTo("no status change matches of kind shipped in project alpha.\n");
  }

  @Test
  void refusesASecondKindWithTheUsage() {
    String said = report.render("shipped done");

    assertThat(said).isEqualTo("usage: activity [created|shipped|deployed|stopped|done] [<project>]\n");
  }

  @Test
  void showsEveryChangeWithNoCutNoteWhenThereAreExactlyTwoHundred() {
    TaskState busy =
        TaskState.builder("alpha", "/wt", TaskStatus.IN_PROGRESS)
            .history(
                Collections.nCopies(200, new StatusChange(TaskStatus.IN_PROGRESS, 1_000L, null)))
            .build();
    when(finished.all()).thenReturn(List.of());
    when(state.tasks()).thenReturn(Map.of("ABC-1", busy));

    String said = report.render("");

    assertThat(said).doesNotContain("the newest");
  }

  @Test
  void showsOnlyTheNewestTwoHundredChangesAndSaysHowManyThereAre() {
    TaskState busy =
        TaskState.builder("alpha", "/wt", TaskStatus.IN_PROGRESS)
            .history(
                Collections.nCopies(201, new StatusChange(TaskStatus.IN_PROGRESS, 1_000L, null)))
            .build();
    when(finished.all()).thenReturn(List.of());
    when(state.tasks()).thenReturn(Map.of("ABC-1", busy));

    String said = report.render("");

    assertThat(said).contains("the newest 200 of 201");
    assertThat(said.lines().filter(line -> line.contains("ABC-1")).count()).isEqualTo(200);
  }
}
