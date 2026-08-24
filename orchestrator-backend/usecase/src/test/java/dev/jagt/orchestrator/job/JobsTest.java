package dev.jagt.orchestrator.job;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobsTest {

    @Test
    void holdsAJobBackUntilItsIntervalHasPassed() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        when(job.every()).thenReturn(Duration.ofMinutes(1));
        Jobs jobs = new Jobs(List.of(job), Runnable::run);

        jobs.tick(1_000);
        jobs.tick(60_000);

        verify(job, times(1)).run();
    }

    @Test
    void runsAJobAgainOnceItsIntervalHasPassed() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        when(job.every()).thenReturn(Duration.ofMinutes(1));
        Jobs jobs = new Jobs(List.of(job), Runnable::run);

        jobs.tick(1_000);
        jobs.tick(61_001);

        verify(job, times(2)).run();
    }

    @Test
    void runsAJobWithNoIntervalOnceAndThenReportsNoNextRun() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("scan-orphans");
        when(job.every()).thenReturn(null);
        Jobs jobs = new Jobs(List.of(job), Runnable::run);

        jobs.tick(1_000);
        jobs.tick(5_000_000);

        verify(job, times(1)).run();
        assertThat(jobs.statuses(5_000_000)).singleElement().extracting(Jobs.Status::nextRunAt).isNull();
    }

    @Test
    void booksAFailedRunAgainstItsOwnJobAndStillRunsTheRest() {
        Job failing = mock(Job.class);
        when(failing.id()).thenReturn("archive-logs");
        doThrow(new IllegalStateException("disk full")).when(failing).run();
        Job healthy = mock(Job.class);
        when(healthy.id()).thenReturn("poll-reviews");
        Jobs jobs = new Jobs(List.of(failing, healthy), Runnable::run);

        jobs.tick(1_000);

        verify(healthy).run();
        assertThat(jobs.statuses(1_000)).extracting(Jobs.Status::id, Jobs.Status::lastError)
                .containsExactly(tuple("archive-logs", "java.lang.IllegalStateException: disk full"),
                        tuple("poll-reviews", null));
    }

    @Test
    void refusesTwoJobsDeclaringTheSameId() {
        Job first = mock(Job.class);
        when(first.id()).thenReturn("poll-reviews");
        Job second = mock(Job.class);
        when(second.id()).thenReturn("poll-reviews");

        assertThatThrownBy(() -> new Jobs(List.of(first, second), Runnable::run))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Two jobs declare the id 'poll-reviews'");
    }

    /** A test doubles out a job by mocking it, and a mock names nothing — an unnamed job is inert, not fatal. */
    @Test
    void leavesAJobThatNamesItselfNothingUnregisteredInsteadOfRefusingToStart() {
        Job nameless = mock(Job.class);
        Job named = mock(Job.class);
        when(named.id()).thenReturn("poll-reviews");
        Jobs jobs = new Jobs(List.of(nameless, named), Runnable::run);

        jobs.tick(1_000);

        verify(nameless, never()).run();
        verify(named).run();
        assertThat(jobs.statuses(1_000)).extracting(Jobs.Status::id).containsExactly("poll-reviews");
    }

    @Test
    void reportsAJobThatHasNeverRunAsSuch() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        when(job.describe()).thenReturn("reads open review requests");
        when(job.every()).thenReturn(Duration.ofMinutes(1));
        Jobs jobs = new Jobs(List.of(job), Runnable::run);

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
        Jobs jobs = new Jobs(List.of(soon, later), Runnable::run);

        jobs.tick(1_000);

        assertThat(jobs.summary(1_000)).isEqualTo(new Jobs.Summary(2, 61_000L, 0));
    }

    @Test
    void summaryCountsAJobWhoseLastRunThrew() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        when(job.every()).thenReturn(Duration.ofMinutes(1));
        doThrow(new IllegalStateException("host unreachable")).when(job).run();
        Jobs jobs = new Jobs(List.of(job), Runnable::run);

        jobs.tick(1_000);

        assertThat(jobs.summary(1_000).failing()).isEqualTo(1);
    }

    @Test
    void retriesAJobThatCouldNotSayHowOftenItWantsToRun() {
        Job job = mock(Job.class);
        when(job.id()).thenReturn("poll-reviews");
        when(job.every()).thenThrow(new IllegalStateException("config unreadable"));
        Jobs jobs = new Jobs(List.of(job), Runnable::run);

        jobs.tick(1_000);
        jobs.tick(2_000);

        verify(job, times(2)).every();
    }

    @Test
    void reportsAJobThatCannotSayHowOftenItWantsToRunAlongsideTheRest() {
        Job mute = mock(Job.class);
        when(mute.id()).thenReturn("poll-reviews");
        when(mute.every()).thenThrow(new IllegalStateException("config unreadable"));
        Job healthy = mock(Job.class);
        when(healthy.id()).thenReturn("clean-recents");
        when(healthy.every()).thenReturn(Duration.ofHours(1));
        Jobs jobs = new Jobs(List.of(mute, healthy), Runnable::run);

        assertThat(jobs.statuses(1_000)).extracting(Jobs.Status::id, Jobs.Status::every)
                .containsExactly(tuple("poll-reviews", null), tuple("clean-recents", Duration.ofHours(1)));
    }
}
