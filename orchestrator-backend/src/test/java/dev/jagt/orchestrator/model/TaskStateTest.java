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
        TaskState legacy = legacyTask(TaskStatus.REVIEW_PENDING, 5_000, null, null);

        assertThat(legacy.history()).isEmpty();
        assertThat(legacy.statusSince()).isEqualTo(5_000);
    }

    @Test
    void recordsAShippedRoundEvenThoughTheStatusDoesNotChange() {
        // Move.shippable allows another round from CI_POLLING, and that is the NORMAL path — so the round has
        // to be visible even though CI_POLLING → CI_POLLING looks like a keep-alive to withStatus.
        TaskState polling = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/mr/9").mrCreatedAt(1_000L).lastPolledAt(9_000L).build();

        TaskState round = polling.withReviewRound("https://host/mr/9");

        assertThat(round.history()).hasSize(polling.history().size() + 1);
        assertThat(round.history().getLast().status()).isEqualTo(TaskStatus.CI_POLLING);
        assertThat(round.statusSince()).isEqualTo(round.history().getLast().at());
        // A new round is a new polling window, and it should be looked at on the next tick.
        assertThat(round.mrCreatedAt()).isGreaterThan(1_000L);
        assertThat(round.lastPolledAt()).isZero();
    }

    @Test
    void keepsTheStatusStampOfALegacyTaskWhenTheAgentOnlyPingsItsKeepAlive() {
        // A task from a state.json written before history existed: statusSince used to fall back to
        // lastActiveTimestamp, which the keep-alive bumps, so an hour-old status read as "0m" forever.
        long anHourAgo = System.currentTimeMillis() - 3_600_000L;
        TaskState legacy = legacyTask(TaskStatus.IN_PROGRESS, anHourAgo, "working", List.of());

        TaskState afterKeepAlive = legacy.touched();

        assertThat(afterKeepAlive.statusSince()).isEqualTo(anHourAgo);
        assertThat(afterKeepAlive.lastActiveTimestamp()).isGreaterThan(anHourAgo);
    }

    /**
     * A task as an OLD state.json holds it: no history at all. The builder cannot express that (a null history
     * means "brand new", so it seeds one), which leaves the canonical constructor — and that is a row of
     * positional nulls that breaks on every new field, so it lives in exactly one place.
     */
    private static TaskState legacyTask(TaskStatus status, long lastActive, String message,
                                        List<StatusChange> history) {
        return new TaskState("proj", "/wt", status, lastActive, message, "a1", null, null, null, null,
                0, 0, null, null, null, history);
    }
}
