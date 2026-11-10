package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CostPerAcceptedTaskTest {

  private static final long WEEK = 7L * 24 * 3600 * 1000;

  @Test
  void countsWhatFailedAndStoppedTaskCostButOnlyShippedTaskAsAccepted() {
    var shipped =
        ReachedTasks.reaching(
            "ABC-1", TaskStatus.DEPLOYED, 50L, 100L, new FinishedTask.Spend(0, 2.0, null, null));
    var failed =
        ReachedTasks.reaching(
            "ABC-2", TaskStatus.NEW, 50L, 100L, new FinishedTask.Spend(0, 3.0, null, null));
    var unread =
        ReachedTasks.reaching(
            "ABC-3", TaskStatus.NEW, 50L, 100L, new FinishedTask.Spend(0, null, null, null));
    var lastWeek =
        ReachedTasks.reaching(
            "ABC-4",
            TaskStatus.DEPLOYED,
            50L,
            WEEK + 100L,
            new FinishedTask.Spend(0, 9.0, null, null));

    assertThat(CostPerAcceptedTask.over(List.of(shipped, failed, unread, lastWeek), 0L))
        .isEqualTo(new CostPerAcceptedTask(1, 3, 5.0, true));
  }

  @Test
  void weekRunsFromItsFirstInstantUpToButNotIncludingTheNextWeeksFirst() {
    var first =
        ReachedTasks.reaching(
            "ABC-1", TaskStatus.DEPLOYED, 5L, 10L, new FinishedTask.Spend(0, 4.0, null, null));
    var second =
        ReachedTasks.reaching(
            "ABC-3", TaskStatus.DEPLOYED, 5L, 11L, new FinishedTask.Spend(0, 2.0, null, null));
    var next =
        ReachedTasks.reaching(
            "ABC-2",
            TaskStatus.DEPLOYED,
            5L,
            10L + WEEK,
            new FinishedTask.Spend(0, 8.0, null, null));

    assertThat(CostPerAcceptedTask.over(List.of(first, second, next), 10L).perAccepted())
        .contains(3.0);
  }

  @Test
  void isAnExactNumberOnlyWhereEveryCostIsKnownAndPriced() {
    var known =
        ReachedTasks.reaching(
            "ABC-1", TaskStatus.DEPLOYED, 5L, 10L, new FinishedTask.Spend(0, 4.0, null, null));
    var unpriced =
        ReachedTasks.reaching(
            "ABC-2",
            TaskStatus.DEPLOYED,
            5L,
            10L,
            new FinishedTask.Spend(0, 1.0, null, TokenUsage.ofUnpricedCall(10, 0, 10)));

    assertThat(CostPerAcceptedTask.over(List.of(known), 0L).lowerBound()).isFalse();
    assertThat(CostPerAcceptedTask.over(List.of(known, unpriced), 0L).lowerBound()).isTrue();
  }

  @Test
  void namesNoNumberWhereNothingShippedInTheWeek() {
    var failed =
        ReachedTasks.reaching(
            "ABC-2", TaskStatus.NEW, 50L, 100L, new FinishedTask.Spend(0, 3.0, null, null));

    assertThat(CostPerAcceptedTask.over(List.of(failed), 0L).perAccepted()).isEmpty();
    assertThat(CostPerAcceptedTask.over(List.of(), 0L).perAccepted()).isEmpty();
  }
}
