package dev.jagt.orchestrator.job.review;

import static dev.jagt.orchestrator.job.TestPollingTaskBuilder.pollingTask;
import static dev.jagt.orchestrator.job.review.TestAutoReviewSchedulerBuilder.autoReviewScheduler;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AutoReviewConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.review.AutoReviewCadence;
import dev.jagt.orchestrator.service.review.ReviewSweepService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.StatusChange;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class AutoReviewSchedulerTest {

  private static final AutoReviewCadence CADENCE =
      new AutoReviewCadence(true, Duration.ofHours(24), 10, 60, 240);
  private static final long NOW = 1_000_000_000_000L;

  private final long now = System.currentTimeMillis();
  private final TaskState due =
      pollingTask()
          .withMrCreatedAt(now - Duration.ofMinutes(30).toMillis())
          .withLastPolledAt(now - Duration.ofMinutes(30).toMillis())
          .build();
  private final TaskState windowElapsed =
      pollingTask().withMrCreatedAt(now - Duration.ofHours(25).toMillis()).build();
  private final TaskState closedOther =
      pollingTask()
          .withStatus(TaskStatus.DONE)
          .withMrUrl("http://mr/2")
          .withMrCreatedAt(now - Duration.ofMinutes(30).toMillis())
          .build();
  private final ReviewSweepService sweep = mock(ReviewSweepService.class);
  private final ConfigService config = mock(ConfigService.class);

  @TempDir Path root;

  private StateService state;
  private AutoReviewScheduler scheduler;

  @BeforeEach
  void schedulerOverAnEmptyStateWithAutoReviewOn() {
    state = stateService().withRoot(root).build();
    when(config.load())
        .thenReturn(
            ConfigFile.defaults().withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
    scheduler =
        autoReviewScheduler()
            .withRoot(root)
            .build(state, config, sweep, Runnable::run, Clock.systemUTC());
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {
        "http://mr/1, true, 999998800000, 999998800000, POLL",
        "http://mr/1, true, 999998800000, 999999880000, SKIP",
        "http://mr/1, true, 999910000000, 0, WINDOW_ELAPSED",
        "http://mr/1, false, 999998800000, 0, SKIP",
        "null, true, 999998800000, 0, SKIP",
        "http://mr/1, true, 1000000000000, 999999400000, POLL"
      })
  void decidesFromTheOptInTheRequestAndTheRoundsClocks(
      String mrUrl,
      boolean autoReview,
      long mrCreatedAt,
      long lastPolledAt,
      AutoReviewScheduler.Action expected) {
    TaskState task =
        pollingTask()
            .withMrUrl(mrUrl)
            .withAutoReview(autoReview)
            .withMrCreatedAt(mrCreatedAt)
            .withLastPolledAt(lastPolledAt)
            .build();

    assertThat(AutoReviewScheduler.decide(task, CADENCE, NOW)).isEqualTo(expected);
  }

  @Test
  void sweepsDueTask() {
    state.putTask("ABC-1", due);

    scheduler.run();

    verify(sweep).sweep("ABC-1");
  }

  @Test
  void keepsPollingRoundThatCameBackCleanUntilSomebodyApprovesIt() {
    state.putTask(
        "ABC-1",
        pollingTask()
            .withStatus(TaskStatus.REVIEWED)
            .withMrCreatedAt(now - Duration.ofMinutes(61).toMillis())
            .withLastPolledAt(now - Duration.ofMinutes(61).toMillis())
            .build());

    scheduler.run();

    verify(sweep).sweep("ABC-1");
  }

  @Test
  void pollsNothingWhenTheInstallHasAutoReviewOff() {
    when(config.load()).thenReturn(ConfigFile.defaults());
    state.putTask(
        "ABC-1", pollingTask().withMrCreatedAt(now - Duration.ofHours(1).toMillis()).build());

    scheduler.run();

    verifyNoInteractions(sweep);
  }

  @Test
  void sweepsNothingWhereTheTaskMadeTwentyUnattendedMovesInTheHour() {
    List<StatusChange> moves =
        java.util.stream.IntStream.range(0, 20)
            .mapToObj(i ->
                new StatusChange(TaskStatus.CI_POLLING, now - 1_000L - i, ActionOrigin.AUTO_REVIEW))
            .toList();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .mrUrl("http://mr/1")
            .autoReview(true)
            .history(moves)
            .mrCreatedAt(due.mrCreatedAt())
            .lastPolledAt(due.lastPolledAt())
            .build());

    scheduler.run();

    verify(sweep, never()).sweep("ABC-1");
  }

  @ParameterizedTest
  @EnumSource(
      value = ToldOnce.Kind.class,
      names = {"AUTO_REVIEW_WINDOW", "AUTO_REVIEW_UNREADABLE"})
  void forgetsOnlyTheAutoReviewReminderOfTaskThatLeftPolling(ToldOnce.Kind reminder) {
    ToldOnce told = ToldOnce.none().with(reminder, "5").with(ToldOnce.Kind.SILENCE, "9");
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.DONE)
            .mrUrl("http://mr/1")
            .autoReview(true)
            .told(told)
            .build());

    scheduler.run();

    assertThat(state.task("ABC-1").orElseThrow().toldOrNone().entries())
        .containsOnlyKeys(ToldOnce.Kind.SILENCE);
  }

  @Test
  void stampsThePollAtTheInstantTheClockGave() {
    state.putTask(
        "ABC-1",
        pollingTask().withMrCreatedAt(999_998_200_000L).withLastPolledAt(999_998_200_000L).build());
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000_000_000_000L), ZoneOffset.UTC);

    autoReviewScheduler().withRoot(root).build(state, config, sweep, Runnable::run, clock).run();

    assertThat(state.task("ABC-1").orElseThrow().lastPolledAt()).isEqualTo(1_000_000_000_000L);
  }

  @Test
  void submitsTaskOnceWhileItsEarlierPollIsStillRunning() {
    state.putTask("ABC-1", due);
    List<Runnable> queued = new ArrayList<>();
    AutoReviewScheduler queuing =
        autoReviewScheduler()
            .withRoot(root)
            .build(state, config, sweep, queued::add, Clock.systemUTC());

    queuing.run();
    queuing.run();

    assertThat(queued).hasSize(1);
  }

  @Nested
  class WhereTheReviewWindowElapsed {

    @Test
    void tapsTheHumanOncePerElapsedWindowInsteadOfPollingOn() {
      state.putTask("ABC-1", windowElapsed);

      scheduler.run();
      scheduler.run();

      verifyNoInteractions(sweep);
    }

    @Test
    void pingsAgainForTheNewWindowShippedRoundOpens() {
      state.putTask("ABC-1", windowElapsed);
      scheduler.run();

      state.putTask(
          "ABC-1", pollingTask().withMrCreatedAt(now - Duration.ofHours(26).toMillis()).build());
      scheduler.run();

      assertThat(
              state.task("ABC-1").orElseThrow().toldOrNone().has(ToldOnce.Kind.AUTO_REVIEW_WINDOW))
          .isTrue();
    }

    @Test
    void forgetsTheRemindersOfTaskThatWasRetiredWhileStillPolling() {
      state.putTask("ABC-1", windowElapsed);
      scheduler.run();

      state.removeTask("ABC-1");
      scheduler.run();
      state.putTask("ABC-1", windowElapsed);
      scheduler.run();

      assertThat(
              state.task("ABC-1").orElseThrow().toldOrNone().has(ToldOnce.Kind.AUTO_REVIEW_WINDOW))
          .isTrue();
    }

    @Test
    void pingsAgainForRoundWhoseTaskLeftAutoReviewAndCameBack() {
      state.putTask("ABC-1", windowElapsed);
      scheduler.run();

      state.putTask(
          "ABC-1",
          pollingTask()
              .withStatus(TaskStatus.DONE)
              .withMrCreatedAt(windowElapsed.mrCreatedAt())
              .build());
      scheduler.run();
      state.putTask("ABC-1", windowElapsed);
      scheduler.run();

      assertThat(
              state.task("ABC-1").orElseThrow().toldOrNone().has(ToldOnce.Kind.AUTO_REVIEW_WINDOW))
          .isTrue();
    }

    @Test
    void tapsOnceForAnElapsedWindowWhileAnotherTaskIsClosed() {
      state.putTask("ABC-1", windowElapsed);
      state.putTask("ABC-2", closedOther);

      scheduler.run();
      scheduler.run();

      assertThat(
              state.task("ABC-1").orElseThrow().toldOrNone().has(ToldOnce.Kind.AUTO_REVIEW_WINDOW))
          .isTrue();
    }
  }

  @Nested
  class WhereNobodyCouldReadTheRound {

    @BeforeEach
    void sweepUnreadable() {
      when(sweep.sweep("ABC-1"))
          .thenReturn(
              new ReviewSweepService.SweepResult(
                  ReviewSweepService.SweepResult.Kind.UNREADABLE,
                  "error: read failed: http://mr/1"));
    }

    @Test
    void tapsTheHumanOnceForRoundNobodyCouldReadInsteadOfOnlyLoggingIt() {
      state.putTask("ABC-1", due);

      scheduler.run();
      state.updateTask("ABC-1", task -> task.withLastPolledAt(0));
      scheduler.run();

      verify(sweep, times(2)).sweep("ABC-1");
    }

    @Test
    void tapsTheHumanAtTheFirstUnreadableRound() {
      state.putTask("ABC-1", due);

      scheduler.run();

      assertThat(
              state
                  .task("ABC-1")
                  .orElseThrow()
                  .toldOrNone()
                  .has(ToldOnce.Kind.AUTO_REVIEW_UNREADABLE))
          .isTrue();
    }

    @Test
    void tapsOnceForAnUnreadableRoundWhileAnotherTaskIsClosed() {
      state.putTask("ABC-1", due);
      state.putTask("ABC-2", closedOther);

      scheduler.run();
      state.updateTask("ABC-1", task -> task.withLastPolledAt(0));
      scheduler.run();

      assertThat(
              state
                  .task("ABC-1")
                  .orElseThrow()
                  .toldOrNone()
                  .has(ToldOnce.Kind.AUTO_REVIEW_UNREADABLE))
          .isTrue();
    }

    @Test
    void tapsAgainForAnUnreadableRoundWhoseTaskWasClosedAndReopened() {
      state.putTask("ABC-1", due);
      scheduler.run();

      state.putTask(
          "ABC-1",
          pollingTask().withStatus(TaskStatus.DONE).withMrCreatedAt(due.mrCreatedAt()).build());
      scheduler.run();
      state.putTask(
          "ABC-1", pollingTask().withMrCreatedAt(due.mrCreatedAt()).withLastPolledAt(1).build());
      scheduler.run();

      assertThat(
              state
                  .task("ABC-1")
                  .orElseThrow()
                  .toldOrNone()
                  .has(ToldOnce.Kind.AUTO_REVIEW_UNREADABLE))
          .isTrue();
    }

    @Test
    void tapsAgainForAnUnreadableRoundWhoseTaskWasRetiredAndCameBack() {
      state.putTask("ABC-1", due);
      scheduler.run();

      state.removeTask("ABC-1");
      scheduler.run();
      state.putTask("ABC-1", due);
      scheduler.run();

      assertThat(
              state
                  .task("ABC-1")
                  .orElseThrow()
                  .toldOrNone()
                  .has(ToldOnce.Kind.AUTO_REVIEW_UNREADABLE))
          .isTrue();
    }
  }
}
