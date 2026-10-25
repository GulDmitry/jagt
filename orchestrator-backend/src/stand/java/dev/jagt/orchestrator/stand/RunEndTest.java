package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RunEndTest {

    @Test
    void aJudgeRunIsHeldOnceTheTimelineWasQuietForAMinuteWithNothingToPress() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("IN_PROGRESS");
        when(timeline.stalledFor(Duration.ofSeconds(60))).thenReturn(true);

        boolean held = new RunEnd(StandMode.JUDGE, false).held(timeline, true);

        assertThat(held).isTrue();
    }

    @Test
    void aJudgeRunIsNotHeldWhileTheTimelineStillMoves() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("IN_PROGRESS");
        when(timeline.stalledFor(Duration.ofSeconds(60))).thenReturn(false);

        boolean held = new RunEnd(StandMode.JUDGE, false).held(timeline, true);

        assertThat(held).isFalse();
    }

    @Test
    void anActRunIsNotHeldOnNothingToPressBecauseTheMasterPressesOnYourTurn() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("IN_PROGRESS");
        when(timeline.stalledFor(Duration.ofSeconds(60))).thenReturn(true);

        boolean held = new RunEnd(StandMode.ACT, false).held(timeline, true);

        assertThat(held).isFalse();
    }

    @Test
    void anActRunIsHeldAfterTenMinutesWithoutAChange() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("IN_PROGRESS");
        when(timeline.stalledFor(Duration.ofMinutes(10))).thenReturn(true);

        boolean held = new RunEnd(StandMode.ACT, false).held(timeline, true);

        assertThat(held).isTrue();
    }

    @Test
    void aDeployConflictRunIsHeldAsSoonAsTheConflictStepReachedTheSession() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("DEPLOY_CONFLICT");
        when(timeline.relayed("conflict")).thenReturn(true);

        boolean held = new RunEnd(StandMode.ACT, true).held(timeline, true);

        assertThat(held).isTrue();
    }

    @Test
    void aDeployConflictRunIsHeldAfterAMinuteWithoutTheConflictStep() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("DEPLOY_CONFLICT");
        when(timeline.stalledFor(Duration.ofSeconds(60))).thenReturn(true);

        boolean held = new RunEnd(StandMode.ACT, true).held(timeline, true);

        assertThat(held).isTrue();
    }

    @Test
    void aDeployConflictRunWaitsWhileTheConflictStepIsStillComing() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("DEPLOY_CONFLICT");

        boolean held = new RunEnd(StandMode.ACT, true).held(timeline, true);

        assertThat(held).isFalse();
    }

    @Test
    void aReachedRunWaitsForAnEventNotFiredYet() {
        Timeline timeline = mock(Timeline.class);

        boolean reached = new RunEnd(StandMode.ACT, false).reached(true, true, timeline);

        assertThat(reached).isFalse();
    }

    @Test
    void aReachedRunStopsWaitingForAnEventAfterAQuietMinute() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.stalledFor(Duration.ofSeconds(60))).thenReturn(true);

        boolean reached = new RunEnd(StandMode.ACT, false).reached(true, true, timeline);

        assertThat(reached).isTrue();
    }

    @Test
    void aReachedRunWithNoEventPendingEndsAtOnce() {
        Timeline timeline = mock(Timeline.class);

        boolean reached = new RunEnd(StandMode.ACT, false).reached(true, false, timeline);

        assertThat(reached).isTrue();
    }

    @Test
    void aRunEndsOnTheReviewerOnceTheSweepsWaitedAndNothingElseMovedForThreeSeconds() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.stalledFor(Duration.ofSeconds(3))).thenReturn(true);

        boolean waiting = new RunEnd(StandMode.ACT, false).waitingOnTheReviewer(true, timeline);

        assertThat(waiting).isTrue();
    }

    @Test
    void aRunKeepsGoingWhileSomethingElseStillMovesBesideTheWaitingSweeps() {
        Timeline timeline = mock(Timeline.class);

        boolean waiting = new RunEnd(StandMode.ACT, false).waitingOnTheReviewer(true, timeline);

        assertThat(waiting).isFalse();
    }

    @Test
    void aDeployConflictNoMutationExpectedIsNotHeldAtTheConflictRelay() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("DEPLOY_CONFLICT");
        when(timeline.relayed("conflict")).thenReturn(true);

        boolean held = new RunEnd(StandMode.ACT, false).held(timeline, true);

        assertThat(held).isFalse();
    }
}
