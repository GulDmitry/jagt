package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.StatusChange;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TaskStateStandingTest {

  @Test
  void startsItsHistoryAtTheStatusItWasCreatedWith() {
    TaskState created =
        TaskState.builder("proj", "/wt", TaskStatus.NEW).lastActiveTimestamp(1_000).build();

    assertThat(created.standing().history())
        .containsExactly(new StatusChange(TaskStatus.NEW, 1_000, null));
  }

  @Test
  void recordsEveryMoveInTheOrderItHappened() {
    TaskState shipped =
        TaskState.builder("proj", "/wt", TaskStatus.NEW)
            .lastActiveTimestamp(1_000)
            .build()
            .withStatus(TaskStatus.IN_PROGRESS, "working", 2_000)
            .withStatus(TaskStatus.REVIEW_PENDING, "done", 2_000)
            .withStatus(TaskStatus.SHIPPING, "shipping", 2_000);

    assertThat(shipped.standing().history())
        .extracting(StatusChange::status)
        .containsExactly(
            TaskStatus.NEW, TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING, TaskStatus.SHIPPING);
  }

  @Test
  void recordsNothingForKeepAliveBecauseTheStatusDidNotChange() {
    TaskState working =
        TaskState.builder("proj", "/wt", TaskStatus.NEW)
            .lastActiveTimestamp(1_000)
            .build()
            .withStatus(TaskStatus.IN_PROGRESS, "working", 2_000);

    TaskState stillWorking = working.touched(2_000).touched(3_000).touched(4_000);

    assertThat(stillWorking.standing().history())
        .extracting(StatusChange::status)
        .containsExactly(TaskStatus.NEW, TaskStatus.IN_PROGRESS);
  }

  @Test
  void dropsTheOldestMovesAfterTheOpeningOneOnceFiftyAreRecordedSoStateJsonStaysSmall() {
    List<StatusChange> fifty =
        IntStream.range(0, 50)
            .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i, null))
            .toList();
    TaskState full =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).history(fifty).build();

    TaskState moved = full.withStatus(TaskStatus.REVIEW_PENDING, "done", 2_000);

    assertThat(moved.standing().history()).hasSize(50);
    assertThat(moved.standing().history().getFirst().at()).isZero();
    assertThat(moved.standing().history().get(1).at()).isEqualTo(2);
    assertThat(moved.standing().history().getLast().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
  }

  @Test
  void reportsTimeInTheCurrentStatusRatherThanTimeSinceTheLastKeepAlive() {
    TaskState working =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 1_000, null)))
            .build();

    TaskState keptAlive = working.touched(2_000);

    assertThat(keptAlive.standing().statusSince()).isEqualTo(1_000);
    assertThat(keptAlive.standing().lastActiveTimestamp()).isEqualTo(2_000);
  }

  @Test
  void reportsTheActivityStampForTaskWrittenBeforeHistoryExisted() {
    TaskState legacy =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .lastActiveTimestamp(5_000)
            .alias("a1")
            .history(List.of())
            .build();

    assertThat(legacy.standing().history()).isEmpty();
    assertThat(legacy.standing().statusSince()).isEqualTo(5_000);
  }

  @Test
  void stampsTheShippedTreeOnTheShipJustTaken() {
    TaskState shipping =
        TaskState.builder("proj", "/wt", TaskStatus.SHIPPING)
            .history(List.of(new StatusChange(TaskStatus.SHIPPING, 1_000, null)))
            .build();

    TaskState stamped = shipping.withLastShipTree("t1");

    assertThat(stamped.standing().history().getLast().tree()).isEqualTo("t1");
  }

  @Test
  void stampsNoTreeOnStepThatIsNotShip() {
    TaskState working =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 1_000, null)))
            .build();

    TaskState stamped = working.withLastShipTree("t1");

    assertThat(stamped.standing().history().getLast().tree()).isNull();
  }

  @Test
  void recordsSomethingDoneToTheTaskEvenWhenItsStatusDoesNotChange() {
    TaskState polling =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .history(List.of(new StatusChange(TaskStatus.CI_POLLING, 1_000, null)))
            .build();

    TaskState again =
        polling.withStatus(TaskStatus.CI_POLLING, "review request: https://host/mr/9", true, 2_000);

    assertThat(again.standing().history()).hasSize(2);
    assertThat(again.standing().statusSince()).isEqualTo(again.standing().history().getLast().at());
  }

  @Test
  void dropsRepeatedStatusTaskOnlyKeepsSayingAboutItself() {
    TaskState polling =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .history(List.of(new StatusChange(TaskStatus.CI_POLLING, 1_000, null)))
            .build();

    TaskState again = polling.withStatus(TaskStatus.CI_POLLING, "still polling", 2_000);

    assertThat(again.standing().history()).hasSize(1);
  }

  @Test
  void keepsTheStatusStampOfLegacyTaskWhenTheAgentOnlyPingsItsKeepAlive() {
    TaskState legacy =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .lastActiveTimestamp(1_700_000_000_000L)
            .message("working")
            .alias("a1")
            .history(List.of())
            .build();

    TaskState afterKeepAlive = legacy.touched(1_700_000_001_000L);

    assertThat(afterKeepAlive.standing().statusSince()).isEqualTo(1_700_000_000_000L);
    assertThat(afterKeepAlive.standing().lastActiveTimestamp()).isEqualTo(1_700_000_001_000L);
  }

  @Test
  void reportsItsHistoryAsCappedOnlyOnceFiftyStepsAreHeld() {
    List<StatusChange> fortyNine =
        IntStream.range(0, 49)
            .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i, null))
            .toList();
    List<StatusChange> fifty =
        IntStream.range(0, 50)
            .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i, null))
            .toList();

    assertThat(
            TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .history(fortyNine)
                .build()
                .standing()
                .historyAtCap())
        .isFalse();
    assertThat(
            TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .history(fifty)
                .build()
                .standing()
                .historyAtCap())
        .isTrue();
  }

  @Test
  void keepsAllFiftyMovesWhenTheFiftiethIsRecorded() {
    List<StatusChange> fortyNine =
        IntStream.range(0, 49)
            .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i, null))
            .toList();
    TaskState almostFull =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).history(fortyNine).build();

    TaskState moved = almostFull.withStatus(TaskStatus.REVIEW_PENDING, "done", 2_000);

    assertThat(moved.standing().history()).hasSize(50);
    assertThat(moved.standing().history().getFirst().at()).isZero();
  }

  @Test
  void keepsTheOpeningStepWhenTheOldestStepsAreTrimmed() {
    List<StatusChange> fifty =
        new java.util.ArrayList<>(List.of(new StatusChange(TaskStatus.NEW, 0, ActionOrigin.BOARD)));
    IntStream.range(1, 50)
        .forEach(i -> fifty.add(new StatusChange(TaskStatus.IN_PROGRESS, i, null)));
    TaskState full =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).history(fifty).build();

    TaskState moved = full.withStatus(TaskStatus.REVIEW_PENDING, "done", 2_000);

    assertThat(moved.standing().history().getFirst())
        .isEqualTo(new StatusChange(TaskStatus.NEW, 0, ActionOrigin.BOARD));
  }

  @Test
  void countsOneRoundForEveryEntryIntoCiPolling() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .history(
                List.of(
                    new StatusChange(TaskStatus.NEW, 1, null),
                    new StatusChange(TaskStatus.CI_POLLING, 2, null),
                    new StatusChange(TaskStatus.IN_PROGRESS, 3, null),
                    new StatusChange(TaskStatus.REVIEW_PENDING, 4, null),
                    new StatusChange(TaskStatus.CI_POLLING, 5, null)))
            .build();

    assertThat(task.standing().rounds()).isEqualTo(2);
  }

  @Test
  void stampsWhoCausedTheLastStepAndNoOtherStep() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .history(
                List.of(
                    new StatusChange(TaskStatus.NEW, 1, ActionOrigin.BOARD),
                    new StatusChange(TaskStatus.REVIEW_PENDING, 2, null)))
            .build();

    TaskState stamped = task.withLastChangeOrigin(ActionOrigin.TRACKER);

    assertThat(stamped.standing().history())
        .extracting(StatusChange::origin)
        .containsExactly(ActionOrigin.BOARD, ActionOrigin.TRACKER);
  }

  @Test
  void stampsNobodyOnTaskThatHasNoStepYet() {
    TaskState task =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"NEW","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0}\
                """,
                TaskState.class);

    assertThat(task.withLastChangeOrigin(ActionOrigin.BOARD)).isSameAs(task);
  }

  @Test
  void seedsLegacyTaskWithoutAnActivityStampAtTheMomentItWasFirstMoved() {
    TaskState legacy =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"IN_PROGRESS","lastActiveTimestamp":0,
                 "mrCreatedAt":0,"lastPolledAt":0}\
                """,
                TaskState.class);

    TaskState moved = legacy.withStatus(TaskStatus.IN_PROGRESS, "ping", true, 1_700_000_005_000L);

    assertThat(moved.standing().history().getFirst().at()).isEqualTo(1_700_000_005_000L);
  }

  @Test
  void stampsTheReasonOnTheLastStepAndNoOtherStep() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .history(
                List.of(
                    new StatusChange(TaskStatus.NEW, 1, ActionOrigin.BOARD),
                    new StatusChange(TaskStatus.REVIEW_PENDING, 2, null)))
            .build();

    TaskState stamped = task.withLastChangeWhy("work done");

    assertThat(stamped.standing().history())
        .extracting(StatusChange::why)
        .containsExactly(null, "work done");
  }
}
