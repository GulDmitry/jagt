package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.stop.BoardStop;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

class JobsTest {

  private final Job job = mock(Job.class);
  private final TaskScheduler scheduler = mock(TaskScheduler.class);
  private final StateService state = mock(StateService.class);
  private final ArgumentCaptor<Runnable> work = ArgumentCaptor.captor();
  private final ArgumentCaptor<Trigger> trigger = ArgumentCaptor.captor();
  private final SimpleTriggerContext ranAtOneSecond =
      new SimpleTriggerContext(
          Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(1_500));

  private Jobs jobs;

  @BeforeEach
  void oneJobDeclared() {
    when(job.id()).thenReturn("poll-reviews");
    jobs =
        new Jobs(
            new Jobs.Catalog(List.of(job), List.of()),
            scheduler,
            Clock.systemUTC(),
            state,
            mock(BoardStop.class));
  }

  @Test
  void schedulesNoJobBeforeTheStartHasPassedItsChecks() {
    verify(scheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
  }

  @Test
  void runsJobsOnTheScheduleOnceTheStartHasPassedItsChecks() {
    jobs.start();
    verify(scheduler).schedule(work.capture(), any(Trigger.class));
    work.getValue().run();

    verify(job).run();
  }

  @Test
  void runsNoJobWhileTheBoardIsStopped() {
    BoardStop stopped = mock(BoardStop.class);
    when(stopped.stopped()).thenReturn(true);
    Job sourced = mock(Job.class);
    when(sourced.id()).thenReturn("audit-check");
    Jobs halted =
        new Jobs(
            new Jobs.Catalog(List.of(job), List.of(() -> List.of(sourced))),
            scheduler,
            Clock.systemUTC(),
            state,
            stopped);
    halted.start();
    verify(scheduler, times(2)).schedule(work.capture(), any(Trigger.class));

    work.getAllValues().forEach(Runnable::run);

    verify(job, never()).run();
    verify(sourced, never()).run();
  }

  @Test
  void dueAtOnceTheFirstTimeItIsAsked() {
    when(job.every()).thenReturn(Duration.ofMinutes(1));
    Jobs fixed =
        new Jobs(
            new Jobs.Catalog(List.of(job), List.of()),
            scheduler,
            Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC),
            state,
            mock(BoardStop.class));
    fixed.start();
    verify(scheduler).schedule(any(Runnable.class), trigger.capture());

    Instant next = trigger.getValue().nextExecution(new SimpleTriggerContext());

    assertThat(next).isEqualTo(Instant.ofEpochMilli(1_000));
  }

  @Test
  void stampsTheNextRunFromTheStartOfTheLastOne() {
    when(job.every()).thenReturn(Duration.ofMinutes(1));
    jobs.start();
    verify(scheduler).schedule(any(Runnable.class), trigger.capture());

    Instant next =
        trigger
            .getValue()
            .nextExecution(
                new SimpleTriggerContext(
                    Instant.ofEpochMilli(1_000),
                    Instant.ofEpochMilli(1_000),
                    Instant.ofEpochMilli(90_000)));

    assertThat(next).isEqualTo(Instant.ofEpochMilli(61_000));
  }

  @Test
  void reportsTheLastIntervalJobNamedWhenItCanNoLongerSayIt() {
    when(job.every())
        .thenReturn(Duration.ofMinutes(1))
        .thenThrow(new IllegalStateException("config unreadable"));
    jobs.start();
    verify(scheduler).schedule(any(Runnable.class), trigger.capture());
    trigger.getValue().nextExecution(ranAtOneSecond);

    assertThat(jobs.statuses(2_000))
        .extracting(Jobs.Status::every)
        .containsExactly(Duration.ofMinutes(1));
  }

  @Test
  void runsJobWithNoIntervalOnceAndThenReportsNoNextRun() {
    when(job.every()).thenReturn(null);
    jobs.start();
    verify(scheduler).schedule(any(Runnable.class), trigger.capture());

    Instant next = trigger.getValue().nextExecution(ranAtOneSecond);

    assertThat(next).isNull();
    assertThat(jobs.statuses(5_000_000))
        .singleElement()
        .extracting(Jobs.Status::nextRunAt)
        .isNull();
  }

  @Test
  void booksFailedRunAgainstItsOwnJobAndStillRunsTheRest() {
    Job failing = mock(Job.class);
    when(failing.id()).thenReturn("archive-logs");
    doThrow(new IllegalStateException("disk full")).when(failing).run();
    Jobs both =
        new Jobs(
            new Jobs.Catalog(List.of(failing, job), List.of()),
            scheduler,
            Clock.systemUTC(),
            state,
            mock(BoardStop.class));
    both.start();
    verify(scheduler, times(2)).schedule(work.capture(), any(Trigger.class));

    work.getAllValues().forEach(Runnable::run);

    verify(job).run();
    assertThat(both.statuses(1_000))
        .extracting(Jobs.Status::id, Jobs.Status::lastError)
        .containsExactly(
            tuple("archive-logs", "java.lang.IllegalStateException: disk full"),
            tuple("poll-reviews", null));
  }

  @Test
  void logsNoErrorForRunWhoseCommandStoppedWithJagt() {
    doThrow(new Processes.Stopped(List.of("claude"))).when(job).run();
    jobs.start();
    verify(scheduler).schedule(work.capture(), any(Trigger.class));
    try (FakeLogCapture log = new FakeLogCapture(Jobs.class)) {
      work.getValue().run();

      assertThat(log.events()).filteredOn(event -> event.getLevel() == Level.ERROR).isEmpty();
    }
  }

  @Test
  void logsJobFailingTheSameWayOnEveryRunAsAnErrorOnce() {
    doThrow(new IllegalStateException("no tmux server")).when(job).run();
    jobs.start();
    verify(scheduler).schedule(work.capture(), any(Trigger.class));
    try (FakeLogCapture log = new FakeLogCapture(Jobs.class)) {
      work.getValue().run();
      work.getValue().run();
      work.getValue().run();

      assertThat(log.events()).filteredOn(event -> event.getLevel() == Level.ERROR).hasSize(1);
    }
  }

  @Test
  void reportsFailingJobRecoveredOnItsFirstRunThatDoesNotThrow() {
    doThrow(new IllegalStateException("no tmux server")).doNothing().when(job).run();
    jobs.start();
    verify(scheduler).schedule(work.capture(), any(Trigger.class));
    try (FakeLogCapture log = new FakeLogCapture(Jobs.class)) {
      work.getValue().run();
      work.getValue().run();

      assertThat(log.events())
          .extracting(ILoggingEvent::getMessage)
          .containsExactly("job failed", "job recovered");
    }
  }

  @ParameterizedTest
  @CsvSource({
    "TASKS,your tasks",
    "ARBITER,the arbiter",
    "TRACKER,the tracker",
    "HOUSEKEEPING,housekeeping"
  })
  void reportsWhomJobWorksForByTheLabelOfWhatItDeclares(Job.Serves serves, String label) {
    when(job.serves()).thenReturn(serves);

    assertThat(jobs.statuses(1_000))
        .singleElement()
        .extracting(Jobs.Status::serves)
        .isEqualTo(label);
  }

  @Test
  void refusesTwoJobsDeclaringTheSameId() {
    Job second = mock(Job.class);
    when(second.id()).thenReturn("poll-reviews");

    assertThatThrownBy(() ->
            new Jobs(
                new Jobs.Catalog(List.of(job, second), List.of()),
                scheduler,
                Clock.systemUTC(),
                state,
                mock(BoardStop.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Two jobs declare the id 'poll-reviews'");
  }

  @Test
  void refusesToStartWithJobThatNamesItselfNothing() {
    Job nameless = mock(Job.class);
    when(nameless.id()).thenReturn(" ");

    assertThatThrownBy(() ->
            new Jobs(
                new Jobs.Catalog(List.of(nameless), List.of()),
                scheduler,
                Clock.systemUTC(),
                state,
                mock(BoardStop.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("declares no id");
  }

  @Test
  void reportsJobThatHasNeverRunAsSuch() {
    when(job.describe()).thenReturn("reads open review requests");
    when(job.every()).thenReturn(Duration.ofMinutes(1));

    assertThat(jobs.statuses(1_000))
        .extracting(
            Jobs.Status::id,
            Jobs.Status::describe,
            Jobs.Status::every,
            Jobs.Status::lastStartedAt,
            Jobs.Status::lastError,
            Jobs.Status::running)
        .containsExactly(
            tuple(
                "poll-reviews",
                "reads open review requests",
                Duration.ofMinutes(1),
                null,
                null,
                false));
  }

  @Test
  void summarisesTheSoonestRunOfAnythingUnattended() {
    when(job.every()).thenReturn(Duration.ofMinutes(1));
    Job later = mock(Job.class);
    when(later.id()).thenReturn("clean-recents");
    when(later.every()).thenReturn(Duration.ofHours(1));
    Jobs both =
        new Jobs(
            new Jobs.Catalog(List.of(job, later), List.of()),
            scheduler,
            Clock.systemUTC(),
            state,
            mock(BoardStop.class));
    both.start();
    verify(scheduler, times(2)).schedule(any(Runnable.class), trigger.capture());

    trigger.getAllValues().forEach(each -> each.nextExecution(ranAtOneSecond));

    assertThat(both.summary(1_000)).isEqualTo(new Jobs.Summary(2, 61_000L, 0));
  }

  @Test
  void summaryCountsJobWhoseLastRunThrew() {
    doThrow(new IllegalStateException("host unreachable")).when(job).run();
    jobs.start();
    verify(scheduler).schedule(work.capture(), any(Trigger.class));

    work.getValue().run();

    assertThat(jobs.summary(1_000).failing()).isEqualTo(1);
  }

  @Test
  void retriesJobThatCouldNotSayHowOftenItWantsToRunOneSecondAfterItsStart() {
    when(job.every()).thenThrow(new IllegalStateException("config unreadable"));
    jobs.start();
    verify(scheduler).schedule(any(Runnable.class), trigger.capture());

    Instant next = trigger.getValue().nextExecution(ranAtOneSecond);

    assertThat(next).isEqualTo(Instant.ofEpochMilli(2_000));
    assertThat(jobs.statuses(1_000))
        .singleElement()
        .extracting(Jobs.Status::lastError)
        .isEqualTo("java.lang.IllegalStateException: config unreadable");
  }

  @Test
  void reportsJobThatCannotSayHowOftenItWantsToRunAlongsideTheRest() {
    when(job.every()).thenThrow(new IllegalStateException("config unreadable"));
    Job healthy = mock(Job.class);
    when(healthy.id()).thenReturn("clean-recents");
    when(healthy.every()).thenReturn(Duration.ofHours(1));
    Jobs both =
        new Jobs(
            new Jobs.Catalog(List.of(job, healthy), List.of()),
            scheduler,
            Clock.systemUTC(),
            state,
            mock(BoardStop.class));

    assertThat(both.statuses(1_000))
        .extracting(Jobs.Status::id, Jobs.Status::every)
        .containsExactly(tuple("poll-reviews", null), tuple("clean-recents", Duration.ofHours(1)));
  }

  @Test
  void runsJobAskedForNowAtOnceInsteadOfAtItsInterval() {
    when(job.every()).thenReturn(Duration.ofMinutes(10));
    ScheduledFuture<?> waiting = mock(ScheduledFuture.class);
    doReturn(waiting).when(scheduler).schedule(any(Runnable.class), any(Trigger.class));
    Jobs fixed =
        new Jobs(
            new Jobs.Catalog(List.of(job), List.of()),
            scheduler,
            Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC),
            state,
            mock(BoardStop.class));
    fixed.start();

    fixed.runNow("poll-reviews");

    verify(waiting).cancel(false);
    verify(scheduler, times(2)).schedule(any(Runnable.class), trigger.capture());
    assertThat(trigger.getValue().nextExecution(new SimpleTriggerContext()))
        .isEqualTo(Instant.ofEpochMilli(5_000));
  }

  @Test
  void refusesToRunNowJobNobodyDeclared() {
    assertThatThrownBy(() -> jobs.runNow("archive-logs"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No job 'archive-logs'")
        .hasMessageContaining("poll-reviews");
  }

  @Test
  void refusesToRunNowJobThatIsAlreadyRunningAndLeavesItsScheduleAlone() {
    jobs.start();
    verify(scheduler).schedule(work.capture(), any(Trigger.class));
    doAnswer(
            running -> {
              assertThatThrownBy(() -> jobs.runNow("poll-reviews"))
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining("'poll-reviews' is running now");
              return null;
            })
        .when(job)
        .run();

    work.getValue().run();

    verify(job).run();
    verify(scheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
  }

  @Test
  void neverStartsJobWhileItsPreviousRunIsStillGoing() {
    jobs.start();
    verify(scheduler).schedule(work.capture(), any(Trigger.class));
    doAnswer(
            running -> {
              work.getValue().run();
              return null;
            })
        .when(job)
        .run();

    work.getValue().run();

    verify(job, times(1)).run();
  }

  @Test
  void handsTaskJobTheTasksTheStateHoldsInsteadOfRunningItBare() {
    TaskJob taskJob = mock(TaskJob.class);
    when(taskJob.id()).thenReturn("task-job");
    Map<String, TaskState> tasks =
        Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());
    when(state.tasks()).thenReturn(tasks);
    Jobs withTaskJob =
        new Jobs(
            new Jobs.Catalog(List.of(taskJob), List.of()),
            scheduler,
            Clock.systemUTC(),
            state,
            mock(BoardStop.class));
    withTaskJob.start();
    verify(scheduler).schedule(work.capture(), any(Trigger.class));

    work.getValue().run();

    verify(taskJob).runOn(tasks);
    verify(taskJob, never()).run();
  }

  @Test
  void schedulesTheJobsSourceHoldsBesideTheDeclaredOnes() {
    Job sourced = mock(Job.class);
    when(sourced.id()).thenReturn("audit-check");

    Jobs both =
        new Jobs(
            new Jobs.Catalog(List.of(job), List.of(() -> List.of(sourced))),
            scheduler,
            Clock.systemUTC(),
            state,
            mock(BoardStop.class));

    assertThat(both.statuses(1_000))
        .extracting(Jobs.Status::id)
        .containsExactly("poll-reviews", "audit-check");
  }
}
