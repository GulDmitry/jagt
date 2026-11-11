package dev.jagt.orchestrator.task;

import static dev.jagt.orchestrator.task.TestFinishedTaskBuilder.finishedTask;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CostPerAcceptedTaskTest {

  private static final long WEEK = 7L * 24 * 3600 * 1000;

  @Test
  void countsWhatFailedAndStoppedTaskCostButOnlyShippedTaskAsAccepted() {
    var shipped =
        finishedTask().withId("ABC-1")
            .withStatusAt(50L)
            .withFinishedAt(100L)
            .withSpend(new FinishedTask.Spend(0, 2.0, null, null))
            .build();
    var failed =
        finishedTask().withId("ABC-2")
            .withStatus(TaskStatus.NEW)
            .withStatusAt(50L)
            .withFinishedAt(100L)
            .withSpend(new FinishedTask.Spend(0, 3.0, null, null))
            .build();
    var unread =
        finishedTask().withId("ABC-3")
            .withStatus(TaskStatus.NEW)
            .withStatusAt(50L)
            .withFinishedAt(100L)
            .withSpend(new FinishedTask.Spend(0, null, null, null))
            .build();
    var lastWeek =
        finishedTask().withId("ABC-4")
            .withStatusAt(50L)
            .withFinishedAt(WEEK + 100L)
            .withSpend(new FinishedTask.Spend(0, 9.0, null, null))
            .build();

    assertThat(CostPerAcceptedTask.over(List.of(shipped, failed, unread, lastWeek), 0L))
        .isEqualTo(new CostPerAcceptedTask(1, 3, 5.0, true));
  }

  @Test
  void weekRunsFromItsFirstInstantUpToButNotIncludingTheNextWeeksFirst() {
    var first =
        finishedTask().withId("ABC-1")
            .build();
    var second =
        finishedTask().withId("ABC-3")
            .withFinishedAt(11L)
            .withSpend(new FinishedTask.Spend(0, 2.0, null, null))
            .build();
    var next =
        finishedTask().withId("ABC-2")
            .withFinishedAt(10L + WEEK)
            .withSpend(new FinishedTask.Spend(0, 8.0, null, null))
            .build();

    assertThat(CostPerAcceptedTask.over(List.of(first, second, next), 10L).perAccepted())
        .contains(3.0);
  }

  @Test
  void isAnExactNumberOnlyWhereEveryCostIsKnownAndPriced() {
    var known =
        finishedTask().withId("ABC-1")
            .build();
    var unpriced =
        finishedTask().withId("ABC-2")
            .withSpend(new FinishedTask.Spend(0, 1.0, null, TokenUsage.ofUnpricedCall(10, 0, 10)))
            .build();

    assertThat(CostPerAcceptedTask.over(List.of(known), 0L).lowerBound()).isFalse();
    assertThat(CostPerAcceptedTask.over(List.of(known, unpriced), 0L).lowerBound()).isTrue();
  }

  @Test
  void namesNoNumberWhereNothingShippedInTheWeek() {
    var failed =
        finishedTask().withId("ABC-2")
            .withStatus(TaskStatus.NEW)
            .withStatusAt(50L)
            .withFinishedAt(100L)
            .withSpend(new FinishedTask.Spend(0, 3.0, null, null))
            .build();

    assertThat(CostPerAcceptedTask.over(List.of(failed), 0L).perAccepted()).isEmpty();
    assertThat(CostPerAcceptedTask.over(List.of(), 0L).perAccepted()).isEmpty();
  }
}
