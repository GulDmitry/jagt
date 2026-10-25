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

        boolean held = new RunEnd(StandMode.JUDGE).held(timeline, true);

        assertThat(held).isTrue();
    }

    @Test
    void aJudgeRunIsNotHeldWhileTheTimelineStillMoves() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("IN_PROGRESS");
        when(timeline.stalledFor(Duration.ofSeconds(60))).thenReturn(false);

        boolean held = new RunEnd(StandMode.JUDGE).held(timeline, true);

        assertThat(held).isFalse();
    }

    @Test
    void anActRunIsNotHeldOnNothingToPressBecauseTheMasterPressesOnYourTurn() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("IN_PROGRESS");
        when(timeline.stalledFor(Duration.ofSeconds(60))).thenReturn(true);

        boolean held = new RunEnd(StandMode.ACT).held(timeline, true);

        assertThat(held).isFalse();
    }

    @Test
    void anActRunIsHeldAfterTenMinutesWithoutAChange() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("IN_PROGRESS");
        when(timeline.stalledFor(Duration.ofMinutes(10))).thenReturn(true);

        boolean held = new RunEnd(StandMode.ACT).held(timeline, true);

        assertThat(held).isTrue();
    }

    @Test
    void aDeployConflictRunIsHeldAsSoonAsTheConflictStepReachedTheSession() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("DEPLOY_CONFLICT");
        when(timeline.relayed("conflict")).thenReturn(true);

        boolean held = new RunEnd(StandMode.ACT).held(timeline, true);

        assertThat(held).isTrue();
    }

    @Test
    void aDeployConflictRunIsHeldAfterAMinuteWithoutTheConflictStep() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("DEPLOY_CONFLICT");
        when(timeline.stalledFor(Duration.ofSeconds(60))).thenReturn(true);

        boolean held = new RunEnd(StandMode.ACT).held(timeline, true);

        assertThat(held).isTrue();
    }

    @Test
    void aDeployConflictRunWaitsWhileTheConflictStepIsStillComing() {
        Timeline timeline = mock(Timeline.class);
        when(timeline.lastOwner()).thenReturn("YOU");
        when(timeline.lastStatus()).thenReturn("DEPLOY_CONFLICT");

        boolean held = new RunEnd(StandMode.ACT).held(timeline, true);

        assertThat(held).isFalse();
    }
}
