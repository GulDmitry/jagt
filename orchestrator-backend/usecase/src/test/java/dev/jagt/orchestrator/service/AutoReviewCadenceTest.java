package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutoReviewCadenceTest {

    private final AutoReviewCadence cadence = new AutoReviewCadence(true, Duration.ofHours(24), 10, 60);

    @ParameterizedTest
    @CsvSource({"0,10", "12,35", "24,60"})
    void backsOffLinearlyFromTheTightestIntervalToTheMaxAcrossTheWindow(int hoursOut, int expectedMinutes) {
        assertThat(cadence.pollInterval(Duration.ofHours(hoursOut))).isEqualTo(Duration.ofMinutes(expectedMinutes));
    }

    @Test
    void watchesATaskOutForReviewAndSaysWhenItWillLookAgain() {
        long shipped = 1_000_000_000_000L;
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(shipped).lastPolledAt(shipped).build();

        AutoReviewWatch watch = cadence.watch(task, shipped + 60_000);

        assertThat(watch.state()).isEqualTo(AutoReviewWatch.State.WATCHING);
        assertThat(watch.nextPollAt()).isEqualTo(shipped + Duration.ofMinutes(10).toMillis());
    }

    @Test
    void watchesNothingWhilePollingIsSwitchedOffForTheInstall() {
        AutoReviewCadence off = new AutoReviewCadence(false, Duration.ofHours(24), 10, 60);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(1L).build();

        assertThat(off.watch(task, 2L).state()).isEqualTo(AutoReviewWatch.State.NONE);
    }

    /** A task created while polling was off keeps its own answer, so it must say so rather than look idle. */
    @Test
    void namesATaskThatOptedOutWhileTheRestAreWatched() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(1L).autoReview(false).build();

        assertThat(cadence.watch(task, 2L).state()).isEqualTo(AutoReviewWatch.State.OFF_FOR_TASK);
    }

    /** A request jagt cannot time is not the same as no request, and silence would read as the second. */
    @Test
    void saysSoAboutARequestWhoseRoundWasNeverStamped() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/x/-/merge_requests/1").build();

        assertThat(cadence.watch(task, 2L).state()).isEqualTo(AutoReviewWatch.State.NO_ROUND);
    }

    /** Every other multi-repo answer comes from ANY repository's request; this one must not be the exception. */
    @Test
    void watchesAMultiRepoTaskWhoseRequestIsOpenOnASiblingRatherThanTheSessionRepository() {
        long shipped = 1_000_000_000_000L;
        TaskState task = TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                        new TaskRepo("web", "/web-wt", null, "https://host/web/-/merge_requests/2", null)),
                TaskStatus.CI_POLLING).mrCreatedAt(shipped).lastPolledAt(shipped).build();

        assertThat(cadence.watch(task, shipped + 1000).state()).isEqualTo(AutoReviewWatch.State.WATCHING);
    }

    @Test
    void reportsTheWindowAsElapsedOnceNothingWillPollAgain() {
        long shipped = 1_000_000_000_000L;
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(shipped).build();

        assertThat(cadence.watch(task, shipped + Duration.ofHours(25).toMillis()).state())
                .isEqualTo(AutoReviewWatch.State.WINDOW_ELAPSED);
    }

    @Test
    void watchesNothingAboutATaskThatIsNotOutForReview() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

        assertThat(cadence.watch(task, 2L).state()).isEqualTo(AutoReviewWatch.State.NONE);
    }

    @Test
    void stopsPollingOnceTheWindowHasElapsed() {
        assertThat(cadence.pollInterval(Duration.ofHours(24).plusMinutes(1))).isNull();
    }
}
