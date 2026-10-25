package dev.jagt.orchestrator.job;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

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

class JobsTest {

    @Test
    void schedulesNoJobBeforeTheStartHasPassedItsChecks() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("master-feed");
        TaskScheduler scheduler = mock(TaskScheduler.class);
        new Jobs(List.of(job), scheduler, Clock.systemUTC());

        verify(scheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void runsJobsOnTheScheduleOnceTheStartHasPassedItsChecks() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("master-feed");
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Jobs jobs = new Jobs(List.of(job), scheduler, Clock.systemUTC());
        ArgumentCaptor<Runnable> work = ArgumentCaptor.captor();

        jobs.start();
        verify(scheduler).schedule(work.capture(), any(Trigger.class));
        work.getValue().run();

        verify(job).run();
    }

    @Test
    void dueAtOnceTheFirstTimeItIsAsked() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        when(job.every()).thenReturn(Duration.ofMinutes(1));
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Jobs jobs = new Jobs(List.of(job), scheduler, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));
        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.captor();
        jobs.start();
        verify(scheduler).schedule(any(Runnable.class), trigger.capture());

        Instant next = trigger.getValue().nextExecution(new SimpleTriggerContext());

        assertThat(next).isEqualTo(Instant.ofEpochMilli(1_000));
    }

    @Test
    void stampsTheNextRunFromTheStartOfTheLastOne() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        when(job.every()).thenReturn(Duration.ofMinutes(1));
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Jobs jobs = new Jobs(List.of(job), scheduler, Clock.systemUTC());
        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.captor();
        jobs.start();
        verify(scheduler).schedule(any(Runnable.class), trigger.capture());

        Instant next = trigger.getValue().nextExecution(new SimpleTriggerContext(Instant.ofEpochMilli(1_000),
                Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(90_000)));

        assertThat(next).isEqualTo(Instant.ofEpochMilli(61_000));
    }

    @Test
    void reportsTheLastIntervalAJobNamedWhenItCanNoLongerSayIt() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        when(job.every()).thenReturn(Duration.ofMinutes(1)).thenThrow(new IllegalStateException("config unreadable"));
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Jobs jobs = new Jobs(List.of(job), scheduler, Clock.systemUTC());
        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.captor();
        jobs.start();
        verify(scheduler).schedule(any(Runnable.class), trigger.capture());
        trigger.getValue().nextExecution(new SimpleTriggerContext(Instant.ofEpochMilli(1_000),
                Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(1_500)));

        assertThat(jobs.statuses(2_000)).extracting(Jobs.Status::every).containsExactly(Duration.ofMinutes(1));
    }

    @Test
    void runsAJobWithNoIntervalOnceAndThenReportsNoNextRun() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("scan-orphans");
        when(job.every()).thenReturn(null);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Jobs jobs = new Jobs(List.of(job), scheduler, Clock.systemUTC());
        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.captor();
        jobs.start();
        verify(scheduler).schedule(any(Runnable.class), trigger.capture());

        Instant next = trigger.getValue().nextExecution(new SimpleTriggerContext(Instant.ofEpochMilli(1_000),
                Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(1_500)));

        assertThat(next).isNull();
        assertThat(jobs.statuses(5_000_000)).singleElement().extracting(Jobs.Status::nextRunAt).isNull();
    }

    @Test
    void booksAFailedRunAgainstItsOwnJobAndStillRunsTheRest() {
        Job failing = mock(Job.class);
        when(failing.id()).thenReturn("archive-logs");
        doThrow(new IllegalStateException("disk full")).when(failing).run();
        Job healthy = mock(Job.class);
        when(healthy.id()).thenReturn("poll-reviews");
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Jobs jobs = new Jobs(List.of(failing, healthy), scheduler, Clock.systemUTC());
        ArgumentCaptor<Runnable> work = ArgumentCaptor.captor();
        jobs.start();
        verify(scheduler, times(2)).schedule(work.capture(), any(Trigger.class));

        work.getAllValues().forEach(Runnable::run);

        verify(healthy).run();
        assertThat(jobs.statuses(1_000)).extracting(Jobs.Status::id, Jobs.Status::lastError)
                .containsExactly(tuple("archive-logs", "java.lang.IllegalStateException: disk full"),
                        tuple("poll-reviews", null));
    }

    @ParameterizedTest
    @CsvSource({"TASKS,your tasks", "MASTER,the master", "TRACKER,the tracker", "HOUSEKEEPING,housekeeping"})
    void reportsWhomAJobWorksForByTheLabelOfWhatItDeclares(Job.Serves serves, String label) {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        when(job.serves()).thenReturn(serves);
        Jobs jobs = new Jobs(List.of(job), mock(TaskScheduler.class), Clock.systemUTC());

        assertThat(jobs.statuses(1_000)).singleElement().extracting(Jobs.Status::serves).isEqualTo(label);
    }

    @Test
    void refusesTwoJobsDeclaringTheSameId() {
        Job first = mock(Job.class);
        when(first.id()).thenReturn("poll-reviews");
        Job second = mock(Job.class);
        when(second.id()).thenReturn("poll-reviews");

        assertThatThrownBy(() -> new Jobs(List.of(first, second), mock(TaskScheduler.class), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Two jobs declare the id 'poll-reviews'");
    }

    @Test
    void refusesToStartWithAJobThatNamesItselfNothing() {
        Job nameless = mock(Job.class);
        when(nameless.id()).thenReturn(" ");

        assertThatThrownBy(() -> new Jobs(List.of(nameless), mock(TaskScheduler.class), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declares no id");
    }

    @Test
    void reportsAJobThatHasNeverRunAsSuch() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        when(job.describe()).thenReturn("reads open review requests");
        when(job.every()).thenReturn(Duration.ofMinutes(1));
        Jobs jobs = new Jobs(List.of(job), mock(TaskScheduler.class), Clock.systemUTC());

        assertThat(jobs.statuses(1_000))
                .extracting(Jobs.Status::id, Jobs.Status::describe, Jobs.Status::every,
                        Jobs.Status::lastStartedAt,
                        Jobs.Status::lastError, Jobs.Status::running)
                .containsExactly(tuple("poll-reviews", "reads open review requests", Duration.ofMinutes(1), null,
                        null, false));
    }

    @Test
    void summarisesTheSoonestRunOfAnythingUnattended() {
        Job soon = mock(Job.class);
        when(soon.id()).thenReturn("poll-reviews");
        when(soon.every()).thenReturn(Duration.ofMinutes(1));
        Job later = mock(Job.class);
        when(later.id()).thenReturn("clean-recents");
        when(later.every()).thenReturn(Duration.ofHours(1));
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Jobs jobs = new Jobs(List.of(soon, later), scheduler, Clock.systemUTC());
        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.captor();
        jobs.start();
        verify(scheduler, times(2)).schedule(any(Runnable.class), trigger.capture());
        SimpleTriggerContext ranAtOneSecond = new SimpleTriggerContext(Instant.ofEpochMilli(1_000),
                Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(1_500));

        trigger.getAllValues().forEach(each -> each.nextExecution(ranAtOneSecond));

        assertThat(jobs.summary(1_000)).isEqualTo(new Jobs.Summary(2, 61_000L, 0));
    }

    @Test
    void summaryCountsAJobWhoseLastRunThrew() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        doThrow(new IllegalStateException("host unreachable")).when(job).run();
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Jobs jobs = new Jobs(List.of(job), scheduler, Clock.systemUTC());
        ArgumentCaptor<Runnable> work = ArgumentCaptor.captor();
        jobs.start();
        verify(scheduler).schedule(work.capture(), any(Trigger.class));

        work.getValue().run();

        assertThat(jobs.summary(1_000).failing()).isEqualTo(1);
    }

    @Test
    void retriesAJobThatCouldNotSayHowOftenItWantsToRunOneSecondAfterItsStart() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        when(job.every()).thenThrow(new IllegalStateException("config unreadable"));
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Jobs jobs = new Jobs(List.of(job), scheduler, Clock.systemUTC());
        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.captor();
        jobs.start();
        verify(scheduler).schedule(any(Runnable.class), trigger.capture());

        Instant next = trigger.getValue().nextExecution(new SimpleTriggerContext(Instant.ofEpochMilli(1_000),
                Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(1_500)));

        assertThat(next).isEqualTo(Instant.ofEpochMilli(2_000));
        assertThat(jobs.statuses(1_000)).singleElement().extracting(Jobs.Status::lastError)
                .isEqualTo("java.lang.IllegalStateException: config unreadable");
    }

    @Test
    void reportsAJobThatCannotSayHowOftenItWantsToRunAlongsideTheRest() {
        Job mute = mock(Job.class);
        when(mute.id()).thenReturn("poll-reviews");
        when(mute.every()).thenThrow(new IllegalStateException("config unreadable"));
        Job healthy = mock(Job.class);
        when(healthy.id()).thenReturn("clean-recents");
        when(healthy.every()).thenReturn(Duration.ofHours(1));
        Jobs jobs = new Jobs(List.of(mute, healthy), mock(TaskScheduler.class), Clock.systemUTC());

        assertThat(jobs.statuses(1_000)).extracting(Jobs.Status::id, Jobs.Status::every)
                .containsExactly(tuple("poll-reviews", null), tuple("clean-recents", Duration.ofHours(1)));
    }

    @Test
    void runsAJobAskedForNowAtOnceInsteadOfAtItsInterval() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        when(job.every()).thenReturn(Duration.ofMinutes(10));
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> waiting = mock(ScheduledFuture.class);
        doReturn(waiting).when(scheduler).schedule(any(Runnable.class), any(Trigger.class));
        Jobs jobs = new Jobs(List.of(job), scheduler, Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC));
        jobs.start();

        jobs.runNow("poll-reviews");

        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.captor();
        verify(waiting).cancel(false);
        verify(scheduler, times(2)).schedule(any(Runnable.class), trigger.capture());
        assertThat(trigger.getValue().nextExecution(new SimpleTriggerContext()))
                .isEqualTo(Instant.ofEpochMilli(5_000));
    }

    @Test
    void refusesToRunNowAJobNobodyDeclared() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        Jobs jobs = new Jobs(List.of(job), mock(TaskScheduler.class), Clock.systemUTC());

        assertThatThrownBy(() -> jobs.runNow("archive-logs"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No job 'archive-logs'")
                .hasMessageContaining("poll-reviews");
    }

    @Test
    void refusesToRunNowAJobThatIsAlreadyRunningAndLeavesItsScheduleAlone() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Jobs jobs = new Jobs(List.of(job), scheduler, Clock.systemUTC());
        ArgumentCaptor<Runnable> work = ArgumentCaptor.captor();
        jobs.start();
        verify(scheduler).schedule(work.capture(), any(Trigger.class));
        doAnswer(running -> {
            assertThatThrownBy(() -> jobs.runNow("poll-reviews"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("'poll-reviews' is running now");
            return null;
        }).when(job).run();

        work.getValue().run();

        verify(job).run();
        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void neverStartsAJobWhileItsPreviousRunIsStillGoing() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Jobs jobs = new Jobs(List.of(job), scheduler, Clock.systemUTC());
        ArgumentCaptor<Runnable> work = ArgumentCaptor.captor();
        jobs.start();
        verify(scheduler).schedule(work.capture(), any(Trigger.class));
        doAnswer(running -> {
            work.getValue().run();
            return null;
        }).when(job).run();

        work.getValue().run();

        verify(job, times(1)).run();
    }
}
