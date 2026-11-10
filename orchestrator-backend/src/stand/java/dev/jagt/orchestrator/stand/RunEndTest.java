package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RunEndTest {

  @Test
  void judgeRunIsHeldOnceTheTimelineWasQuietForMinuteWithNothingToPress() {
    Timeline timeline = mock(Timeline.class);
    when(timeline.lastOwner()).thenReturn("YOU");
    when(timeline.lastStatus()).thenReturn("IN_PROGRESS");
    when(timeline.stalledFor(Duration.ofSeconds(60))).thenReturn(true);

    boolean held = new RunEnd(StandMode.JUDGE, false, false).held(timeline, true);

    assertThat(held).isTrue();
  }

  @Test
  void judgeRunIsNotHeldWhileTheTimelineStillMoves() {
    Timeline timeline = mock(Timeline.class);
    when(timeline.lastOwner()).thenReturn("YOU");
    when(timeline.lastStatus()).thenReturn("IN_PROGRESS");
    when(timeline.stalledFor(Duration.ofSeconds(60))).thenReturn(false);

    boolean held = new RunEnd(StandMode.JUDGE, false, false).held(timeline, true);

    assertThat(held).isFalse();
  }

  @Test
  void anActRunIsNotHeldOnNothingToPressBecauseTheMasterPressesOnYourTurn() {
    Timeline timeline = mock(Timeline.class);
    when(timeline.lastOwner()).thenReturn("YOU");
    when(timeline.lastStatus()).thenReturn("IN_PROGRESS");
    when(timeline.stalledFor(Duration.ofSeconds(60))).thenReturn(true);

    boolean held = new RunEnd(StandMode.ACT, false, false).held(timeline, true);

    assertThat(held).isFalse();
  }

  @Test
  void anActRunIsHeldAfterTenMinutesWithoutChange() {
    Timeline timeline = mock(Timeline.class);
    when(timeline.lastOwner()).thenReturn("YOU");
    when(timeline.lastStatus()).thenReturn("IN_PROGRESS");
    when(timeline.stalledFor(Duration.ofMinutes(10))).thenReturn(true);

    boolean held = new RunEnd(StandMode.ACT, false, false).held(timeline, true);

    assertThat(held).isTrue();
  }

  @Test
  void deployConflictRunIsHeldAsSoonAsTheConflictStepReachedTheSession() {
    Timeline timeline = mock(Timeline.class);
    when(timeline.lastOwner()).thenReturn("YOU");
    when(timeline.lastStatus()).thenReturn("DEPLOY_CONFLICT");
    when(timeline.relayed("conflict")).thenReturn(true);

    boolean held = new RunEnd(StandMode.ACT, true, true).held(timeline, true);

    assertThat(held).isTrue();
  }

  @Test
  void deployConflictRunIsHeldAfterMinuteWithoutTheConflictStep() {
    Timeline timeline = mock(Timeline.class);
    when(timeline.lastOwner()).thenReturn("YOU");
    when(timeline.lastStatus()).thenReturn("DEPLOY_CONFLICT");
    when(timeline.stalledFor(Duration.ofSeconds(60))).thenReturn(true);

    boolean held = new RunEnd(StandMode.ACT, true, true).held(timeline, true);

    assertThat(held).isTrue();
  }

  @Test
  void deployConflictRunIsHeldAtOnceWhereNoConflictStepComes() {
    Timeline timeline = mock(Timeline.class);
    when(timeline.lastOwner()).thenReturn("YOU");
    when(timeline.lastStatus()).thenReturn("DEPLOY_CONFLICT");

    boolean held = new RunEnd(StandMode.JUDGE, true, false).held(timeline, true);

    assertThat(held).isTrue();
  }

  @Test
  void deployConflictRunWaitsWhileTheConflictStepIsStillComing() {
    Timeline timeline = mock(Timeline.class);
    when(timeline.lastOwner()).thenReturn("YOU");
    when(timeline.lastStatus()).thenReturn("DEPLOY_CONFLICT");

    boolean held = new RunEnd(StandMode.ACT, true, true).held(timeline, true);

    assertThat(held).isFalse();
  }

  @Test
  void reachedRunWaitsForAnEventNotFiredYet() {
    Timeline timeline = mock(Timeline.class);

    boolean reached = new RunEnd(StandMode.ACT, false, false).reached(true, true, timeline);

    assertThat(reached).isFalse();
  }

  @Test
  void reachedRunStopsWaitingForAnEventAfterQuietMinute() {
    Timeline timeline = mock(Timeline.class);
    when(timeline.stalledFor(Duration.ofSeconds(60))).thenReturn(true);

    boolean reached = new RunEnd(StandMode.ACT, false, false).reached(true, true, timeline);

    assertThat(reached).isTrue();
  }

  @Test
  void reachedRunWithNoEventPendingEndsAtOnce() {
    Timeline timeline = mock(Timeline.class);

    boolean reached = new RunEnd(StandMode.ACT, false, false).reached(true, false, timeline);

    assertThat(reached).isTrue();
  }

  @Test
  void runEndsOnTheReviewerOnceTheSweepsWaitedAndNothingElseMovedForThreeSeconds() {
    Timeline timeline = mock(Timeline.class);
    when(timeline.stalledFor(Duration.ofSeconds(3))).thenReturn(true);

    boolean waiting = new RunEnd(StandMode.ACT, false, false).waitingOnTheReviewer(true, timeline);

    assertThat(waiting).isTrue();
  }

  @Test
  void runKeepsGoingWhileSomethingElseStillMovesBesideTheWaitingSweeps() {
    Timeline timeline = mock(Timeline.class);

    boolean waiting = new RunEnd(StandMode.ACT, false, false).waitingOnTheReviewer(true, timeline);

    assertThat(waiting).isFalse();
  }

  @Test
  void deployConflictNoMutationExpectedIsNotHeldAtTheConflictRelay() {
    Timeline timeline = mock(Timeline.class);
    when(timeline.lastOwner()).thenReturn("YOU");
    when(timeline.lastStatus()).thenReturn("DEPLOY_CONFLICT");
    when(timeline.relayed("conflict")).thenReturn(true);

    boolean held = new RunEnd(StandMode.ACT, false, false).held(timeline, true);

    assertThat(held).isFalse();
  }
}
