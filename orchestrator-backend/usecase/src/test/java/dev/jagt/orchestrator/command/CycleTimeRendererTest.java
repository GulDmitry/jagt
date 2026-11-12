package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CycleTimeRendererTest {

  private static final long HOUR = 3_600_000L;
  private static final long NOW = 1_700_000_000_000L;

  private final TaskState pollingSince5h =
      TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
          .history(List.of(new StatusChange(TaskStatus.CI_POLLING, NOW - 5 * HOUR, null)))
          .build();
  private final TaskState pollingSince3h =
      TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
          .history(List.of(new StatusChange(TaskStatus.CI_POLLING, NOW - 3 * HOUR, null)))
          .build();
  private final TaskState pollingSince5hAndAgain4h =
      TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
          .history(
              List.of(
                  new StatusChange(TaskStatus.CI_POLLING, NOW - 5 * HOUR, null),
                  new StatusChange(TaskStatus.CI_POLLING, NOW - 4 * HOUR, null)))
          .build();

  @Test
  void chargesEachStepToWhoeverOwnedTheStatusItWasSpentIn() {
    Map<String, TaskState> tasks =
        Map.of(
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .history(
                    List.of(
                        new StatusChange(TaskStatus.IN_PROGRESS, NOW - 10 * HOUR, null),
                        new StatusChange(TaskStatus.CI_POLLING, NOW - 8 * HOUR, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, NOW - 6 * HOUR, null)))
                .build());

    String out = new CycleTimeRenderer().render(tasks, NOW);

    assertThat(out.lines().filter(line -> line.startsWith("ABC-1")).findFirst().orElseThrow())
        .containsSubsequence("10h", "6h", "2h", "2h");
  }

  @Test
  void countsOneRoundPerTripOutForReview() {
    Map<String, TaskState> tasks =
        Map.of(
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .history(
                    List.of(
                        new StatusChange(TaskStatus.IN_PROGRESS, NOW - 9 * HOUR, null),
                        new StatusChange(TaskStatus.CI_POLLING, NOW - 8 * HOUR, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, NOW - 7 * HOUR, null),
                        new StatusChange(TaskStatus.CI_POLLING, NOW - 6 * HOUR, null)))
                .build());

    String out = new CycleTimeRenderer().render(tasks, NOW);

    assertThat(out.lines().filter(line -> line.startsWith("ABC-1")).findFirst().orElseThrow())
        .endsWith("2");
  }

  @Test
  void countsUnderByYouOnlyTheMovesHumanMadeOnTheTask() {
    Map<String, TaskState> tasks =
        Map.of(
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .history(
                    List.of(
                        new StatusChange(
                            TaskStatus.IN_PROGRESS, NOW - 9 * HOUR, ActionOrigin.BOARD),
                        new StatusChange(
                            TaskStatus.REVIEW_PENDING, NOW - 8 * HOUR, ActionOrigin.MCP),
                        new StatusChange(
                            TaskStatus.IN_PROGRESS, NOW - 7 * HOUR, ActionOrigin.BOARD),
                        new StatusChange(
                            TaskStatus.REVIEW_PENDING, NOW - 6 * HOUR, ActionOrigin.MASTER),
                        new StatusChange(
                            TaskStatus.CI_POLLING, NOW - 5 * HOUR, ActionOrigin.PALETTE)))
                .build());

    String out = new CycleTimeRenderer().render(tasks, NOW);

    assertThat(out).contains("BY YOU");
    assertThat(out.lines().filter(line -> line.startsWith("ABC-1")).findFirst().orElseThrow())
        .containsPattern("\\s3\\s+\\d+$");
    assertThat(out).contains("by you counts the moves a human made on the task");
  }

  @Test
  void addsTheRoundsUpAcrossTasksAndGivesTheAverageInWords() {
    Map<String, TaskState> tasks =
        Map.of(
            "ABC-1", pollingSince5hAndAgain4h,
            "ABC-2", pollingSince3h);

    String out = new CycleTimeRenderer().render(tasks, NOW);

    assertThat(out.lines().filter(line -> line.startsWith("all tasks")).findFirst().orElseThrow())
        .endsWith("3");
    assertThat(out).contains("1.5 per task");
  }

  @Test
  void namesTheSlowestStepAsShareOfTheTimeAnyoneHeldTheTasks() {
    Map<String, TaskState> tasks =
        Map.of(
            "ABC-1",
            TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .history(
                    List.of(
                        new StatusChange(TaskStatus.IN_PROGRESS, NOW - 10 * HOUR, null),
                        new StatusChange(TaskStatus.REVIEW_PENDING, NOW - 8 * HOUR, null)))
                .build());

    String out = new CycleTimeRenderer().render(tasks, NOW);

    assertThat(out)
        .contains(
            "you have been the slowest step: 8h of the 10h anyone has held these tasks (80%)");
  }

  @Test
  void putsTheTaskWaitingLongestOnTheHumanFirst() {
    Map<String, TaskState> tasks =
        Map.of(
            "ABC-1",
                TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                    .history(
                        List.of(new StatusChange(TaskStatus.REVIEW_PENDING, NOW - 2 * HOUR, null)))
                    .build(),
            "ABC-2",
                TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                    .history(
                        List.of(new StatusChange(TaskStatus.REVIEW_PENDING, NOW - 20 * HOUR, null)))
                    .build());

    String out = new CycleTimeRenderer().render(tasks, NOW);

    assertThat(out.indexOf("ABC-2")).isLessThan(out.indexOf("ABC-1"));
  }

  @Test
  void saysSoWhenNoTaskHasHistoryToAddUp() {
    String out = new CycleTimeRenderer().render(Map.of(), NOW);

    assertThat(out).contains("(no task has a status history yet)").doesNotContain("ROUNDS");
  }

  @Test
  void addsUpAgeAndAgentTimeAcrossTasksAndNamesTheAgentsAsSlowest() {
    Map<String, TaskState> tasks =
        Map.of(
            "ABC-1",
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                    .history(
                        List.of(new StatusChange(TaskStatus.IN_PROGRESS, NOW - 4 * HOUR, null)))
                    .build(),
            "ABC-2",
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                    .history(
                        List.of(new StatusChange(TaskStatus.IN_PROGRESS, NOW - 6 * HOUR, null)))
                    .build());

    String out = new CycleTimeRenderer().render(tasks, NOW);

    assertThat(out.lines().filter(line -> line.startsWith("all tasks")).findFirst().orElseThrow())
        .containsSubsequence("10h", "0m", "10h", "0m");
    assertThat(out)
        .contains(
            "the agents have been the slowest step: 10h of the 10h anyone has held these tasks"
                + " (100%)");
  }

  @Test
  void addsUpCodeHostTimeAcrossTasksAndNamesTheCodeHostAsSlowest() {
    Map<String, TaskState> tasks =
        Map.of(
            "ABC-1", pollingSince3h,
            "ABC-2", pollingSince5h);

    String out = new CycleTimeRenderer().render(tasks, NOW);

    assertThat(out.lines().filter(line -> line.startsWith("all tasks")).findFirst().orElseThrow())
        .containsSubsequence("8h", "0m", "0m", "8h");
    assertThat(out)
        .contains(
            "the code host has been the slowest step: 8h of the 8h anyone has held these tasks"
                + " (100%)");
  }

  @Test
  void leavesTaskWithoutHistoryOutOfTheTableAndTheAverage() {
    Map<String, TaskState> tasks =
        Map.of(
            "ABC-1", pollingSince3h,
            "ABC-2", TaskState.builder("proj", "/wt", TaskStatus.NEW).history(List.of()).build());

    String out = new CycleTimeRenderer().render(tasks, NOW);

    assertThat(out).doesNotContain("ABC-2").contains("1.0 per task");
  }

  @Test
  void totalsTheRoundsOfAllTasksAsPositiveNumber() {
    Map<String, TaskState> tasks =
        Map.of(
            "ABC-1", pollingSince5hAndAgain4h,
            "ABC-2", pollingSince3h);

    String out = new CycleTimeRenderer().render(tasks, NOW);

    assertThat(out.lines().filter(line -> line.startsWith("all tasks")).findFirst().orElseThrow())
        .endsWith(" 3");
    assertThat(out).contains(" 1.5 per task");
  }

  @Test
  void marksNoFigureAsFloorWhileNoTaskHasLostItsOldestSteps() {
    Map<String, TaskState> tasks =
        Map.of(
            "ABC-1", pollingSince5h,
            "ABC-2", pollingSince3h);

    String out = new CycleTimeRenderer().render(tasks, NOW);

    assertThat(out).doesNotContain("+");
  }

  @Nested
  class WhenTheOldestStepsAgedOut {

    private TaskState pollingFiftyTimes;

    @BeforeEach
    void taskHistoryHoldsFiftySteps() {
      pollingFiftyTimes =
          TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
              .history(
                  IntStream.range(0, 50)
                      .mapToObj(
                          step ->
                              new StatusChange(
                                  TaskStatus.CI_POLLING, NOW - (50 - step) * HOUR, null))
                      .toList())
              .build();
    }

    @Test
    void marksTheFiguresAsFloorsForTaskWhoseOldestStepsHaveAgedOut() {
      String out =
          new CycleTimeRenderer()
              .render(
                  Map.of("ABC-1", pollingFiftyTimes), NOW);

      assertThat(out.lines().filter(line -> line.startsWith("ABC-1")).findFirst().orElseThrow())
          .contains("2d+")
          .endsWith("50+");
      assertThat(out).contains("aged out of its history");
    }

    @Test
    void marksTheTotalAsFloorWhenOnlyTheSecondTaskLostItsOldestSteps() {
      Map<String, TaskState> tasks =
          Map.of(
              "ABC-1", pollingSince5h,
              "ABC-2", pollingFiftyTimes);

      String out = new CycleTimeRenderer().render(tasks, NOW);

      assertThat(out).contains("aged out of its history");
    }
  }
}
