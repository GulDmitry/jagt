package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CostPerAcceptedTaskTest {

  private static final long WEEK = 7L * 24 * 3600 * 1000;

  @Test
  void countsWhatAFailedAndAStoppedTaskCostButOnlyAShippedTaskAsAccepted() {
    var shipped =
        new FinishedTask(
            new FinishedTask.Identity("ABC-1", null, null, List.of(), null),
            100L,
            new FinishedTask.Spend(0, 2.0, null, null),
            new FinishedTask.Course(
                List.of(new StatusChange(TaskStatus.DEPLOYED, 50L, ActionOrigin.BOARD)),
                0,
                null,
                Map.of(),
                List.of(),
                null),
            List.of());
    var failed =
        new FinishedTask(
            new FinishedTask.Identity("ABC-2", null, null, List.of(), null),
            100L,
            new FinishedTask.Spend(0, 3.0, null, null),
            new FinishedTask.Course(
                List.of(new StatusChange(TaskStatus.NEW, 50L, ActionOrigin.BOARD)),
                0,
                null,
                Map.of(),
                List.of(),
                null),
            List.of());
    var unread =
        new FinishedTask(
            new FinishedTask.Identity("ABC-3", null, null, List.of(), null),
            100L,
            new FinishedTask.Spend(0, null, null, null),
            new FinishedTask.Course(
                List.of(new StatusChange(TaskStatus.NEW, 50L, ActionOrigin.BOARD)),
                0,
                null,
                Map.of(),
                List.of(),
                null),
            List.of());
    var lastWeek =
        new FinishedTask(
            new FinishedTask.Identity("ABC-4", null, null, List.of(), null),
            WEEK + 100L,
            new FinishedTask.Spend(0, 9.0, null, null),
            new FinishedTask.Course(
                List.of(new StatusChange(TaskStatus.DEPLOYED, 50L, ActionOrigin.BOARD)),
                0,
                null,
                Map.of(),
                List.of(),
                null),
            List.of());

    assertThat(CostPerAcceptedTask.over(List.of(shipped, failed, unread, lastWeek), 0L))
        .isEqualTo(new CostPerAcceptedTask(1, 3, 5.0, true));
  }

  @Test
  void aWeekRunsFromItsFirstInstantUpToButNotIncludingTheNextWeeksFirst() {
    var first =
        new FinishedTask(
            new FinishedTask.Identity("ABC-1", null, null, List.of(), null),
            10L,
            new FinishedTask.Spend(0, 4.0, null, null),
            new FinishedTask.Course(
                List.of(new StatusChange(TaskStatus.DEPLOYED, 5L, ActionOrigin.BOARD)),
                0,
                null,
                Map.of(),
                List.of(),
                null),
            List.of());
    var second =
        new FinishedTask(
            new FinishedTask.Identity("ABC-3", null, null, List.of(), null),
            11L,
            new FinishedTask.Spend(0, 2.0, null, null),
            new FinishedTask.Course(
                List.of(new StatusChange(TaskStatus.DEPLOYED, 5L, ActionOrigin.BOARD)),
                0,
                null,
                Map.of(),
                List.of(),
                null),
            List.of());
    var next =
        new FinishedTask(
            new FinishedTask.Identity("ABC-2", null, null, List.of(), null),
            10L + WEEK,
            new FinishedTask.Spend(0, 8.0, null, null),
            new FinishedTask.Course(
                List.of(new StatusChange(TaskStatus.DEPLOYED, 5L, ActionOrigin.BOARD)),
                0,
                null,
                Map.of(),
                List.of(),
                null),
            List.of());

    assertThat(CostPerAcceptedTask.over(List.of(first, second, next), 10L).perAccepted())
        .contains(3.0);
  }

  @Test
  void isAnExactNumberOnlyWhereEveryCostIsKnownAndPriced() {
    var known =
        new FinishedTask(
            new FinishedTask.Identity("ABC-1", null, null, List.of(), null),
            10L,
            new FinishedTask.Spend(0, 4.0, null, null),
            new FinishedTask.Course(
                List.of(new StatusChange(TaskStatus.DEPLOYED, 5L, ActionOrigin.BOARD)),
                0,
                null,
                Map.of(),
                List.of(),
                null),
            List.of());
    var unpriced =
        new FinishedTask(
            new FinishedTask.Identity("ABC-2", null, null, List.of(), null),
            10L,
            new FinishedTask.Spend(0, 1.0, null, TokenUsage.ofUnpricedCall(10, 0, 10)),
            new FinishedTask.Course(
                List.of(new StatusChange(TaskStatus.DEPLOYED, 5L, ActionOrigin.BOARD)),
                0,
                null,
                Map.of(),
                List.of(),
                null),
            List.of());

    assertThat(CostPerAcceptedTask.over(List.of(known), 0L).lowerBound()).isFalse();
    assertThat(CostPerAcceptedTask.over(List.of(known, unpriced), 0L).lowerBound()).isTrue();
  }

  @Test
  void namesNoNumberWhereNothingShippedInTheWeek() {
    var failed =
        new FinishedTask(
            new FinishedTask.Identity("ABC-2", null, null, List.of(), null),
            100L,
            new FinishedTask.Spend(0, 3.0, null, null),
            new FinishedTask.Course(
                List.of(new StatusChange(TaskStatus.NEW, 50L, ActionOrigin.BOARD)),
                0,
                null,
                Map.of(),
                List.of(),
                null),
            List.of());

    assertThat(CostPerAcceptedTask.over(List.of(failed), 0L).perAccepted()).isEmpty();
    assertThat(CostPerAcceptedTask.over(List.of(), 0L).perAccepted()).isEmpty();
  }
}
