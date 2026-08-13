package dev.jagt.orchestrator.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStateTest {

    @Test
    void startsItsHistoryAtTheStatusItWasCreatedWith() {
        TaskState created = TaskState.builder("proj", "/wt", TaskStatus.NEW)
                .lastActiveTimestamp(1_000).build();

        assertThat(created.history()).containsExactly(new StatusChange(TaskStatus.NEW, 1_000));
    }

    @Test
    void recordsEveryMoveInTheOrderItHappened() {
        TaskState shipped = TaskState.builder("proj", "/wt", TaskStatus.NEW).lastActiveTimestamp(1_000).build()
                .withStatus(TaskStatus.IN_PROGRESS, "working")
                .withStatus(TaskStatus.REVIEW_PENDING, "done")
                .withStatus(TaskStatus.SHIPPING, "shipping");

        assertThat(shipped.history()).extracting(StatusChange::status)
                .containsExactly(TaskStatus.NEW, TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING,
                        TaskStatus.SHIPPING);
    }

    @Test
    void recordsNothingForAKeepAliveBecauseTheStatusDidNotChange() {
        TaskState working = TaskState.builder("proj", "/wt", TaskStatus.NEW).lastActiveTimestamp(1_000).build()
                .withStatus(TaskStatus.IN_PROGRESS, "working");

        TaskState stillWorking = working.touched().touched().touched();

        assertThat(stillWorking.history()).extracting(StatusChange::status)
                .containsExactly(TaskStatus.NEW, TaskStatus.IN_PROGRESS);
    }

    @Test
    void dropsTheOldestMovesOnceFiftyAreRecordedSoStateJsonStaysSmall() {
        List<StatusChange> fifty = IntStream.range(0, 50)
                .mapToObj(i -> new StatusChange(TaskStatus.IN_PROGRESS, i)).toList();
        TaskState full = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).history(fifty).build();

        TaskState moved = full.withStatus(TaskStatus.REVIEW_PENDING, "done");

        assertThat(moved.history()).hasSize(50);
        assertThat(moved.history().getFirst().at()).isEqualTo(1);
        assertThat(moved.history().getLast().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
    }

    @Test
    void reportsTimeInTheCurrentStatusRatherThanTimeSinceTheLastKeepAlive() {
        TaskState working = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 1_000))).build();

        TaskState keptAlive = working.touched();

        assertThat(keptAlive.statusSince()).isEqualTo(1_000);
        assertThat(keptAlive.lastActiveTimestamp()).isGreaterThan(1_000);
    }

    @Test
    void reportsTheActivityStampForATaskWrittenBeforeHistoryExisted() {
        TaskState legacy = new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING, 5_000, null, "a1", null,
                null, null, null, 0, 0, null, null, null);

        assertThat(legacy.history()).isEmpty();
        assertThat(legacy.statusSince()).isEqualTo(5_000);
    }
}
