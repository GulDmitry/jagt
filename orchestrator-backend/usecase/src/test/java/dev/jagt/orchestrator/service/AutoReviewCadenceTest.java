package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutoReviewCadenceTest {

    private final AutoReviewCadence cadence = new AutoReviewCadence(true, Duration.ofHours(24), 10, 60, 240);

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

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVIEWED", "APPROVED", "DEPLOYED"})
    void pollsAtTheLongestIntervalARequestOnlyAReviewerCanMoveOn(TaskStatus status) {
        long shipped = 1_000_000_000_000L;
        TaskState task = TaskState.builder("proj", "/wt", status)
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(shipped).lastPolledAt(shipped).build();

        AutoReviewWatch watch = cadence.watch(task, shipped + 60_000);

        assertThat(watch.nextPollAt()).isEqualTo(shipped + Duration.ofMinutes(60).toMillis());
    }

    @Test
    void watchesNothingWhilePollingIsSwitchedOffForTheInstall() {
        AutoReviewCadence off = new AutoReviewCadence(false, Duration.ofHours(24), 10, 60, 240);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(1L).build();

        assertThat(off.watch(task, 2L).state()).isEqualTo(AutoReviewWatch.State.NONE);
    }

    @Test
    void stopsWatchingATaskJagtStoppedAndCarriesWhyIntoTheHover() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(1L)
                .stop(new TaskStop(TaskStop.Trigger.SHIPS, 2L, "5 ships in 24h")).build();

        AutoReviewWatch watch = cadence.watch(task, 3L);

        assertThat(watch.state()).isEqualTo(AutoReviewWatch.State.LOOP_STOPPED);
        assertThat(watch.note()).startsWith("5 ships in 24h").contains("lift or close");
    }

    @Test
    void carriesWhyJagtStoppedATaskWithNoRequestIntoTheHover() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .stop(new TaskStop(TaskStop.Trigger.MISS, 2L,
                        "no case sends deploy at IN_PROGRESS, set off by master")).build();

        AutoReviewWatch watch = cadence.watch(task, 3L);

        assertThat(watch.note()).startsWith("no case sends deploy at IN_PROGRESS, set off by master")
                .contains("add a case").endsWith("lift or close");
    }

    @Test
    void namesATaskThatOptedOutWhileTheRestAreWatched() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(1L).autoReview(false).build();

        assertThat(cadence.watch(task, 2L).state()).isEqualTo(AutoReviewWatch.State.OFF_FOR_TASK);
    }

    @Test
    void saysSoAboutARequestWhoseRoundWasNeverStamped() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("https://host/x/-/merge_requests/1").build();

        assertThat(cadence.watch(task, 2L).state()).isEqualTo(AutoReviewWatch.State.NO_ROUND);
    }

    @Test
    void watchesAMultiRepoTaskWhoseRequestIsOpenOnASiblingRatherThanTheSessionRepository() {
        long shipped = 1_000_000_000_000L;
        TaskState task = TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"),
                        TaskRepo.of("web", "/web-wt").withMrUrl("https://host/web/-/merge_requests/2")),
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

    @ParameterizedTest
    @CsvSource({"24,60", "36,150", "48,240", "100,240"})
    void aReviewedTaskIsPolledAgainOnAGrowingIntervalPastItsWindow(int hoursOut, int expectedMinutes) {
        long shipped = 1_000_000_000_000L;
        long now = shipped + Duration.ofHours(hoursOut).toMillis();
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(shipped).lastPolledAt(now).build();

        AutoReviewWatch watch = cadence.watch(task, now);

        assertThat(watch.state()).isEqualTo(AutoReviewWatch.State.WATCHING);
        assertThat(watch.nextPollAt() - now).isEqualTo(Duration.ofMinutes(expectedMinutes).toMillis());
    }

    @Test
    void watchesNothingAboutATaskWithNoRequestToRead() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

        assertThat(cadence.watch(task, 2L).state()).isEqualTo(AutoReviewWatch.State.NONE);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = "DONE", mode = EnumSource.Mode.EXCLUDE)
    void pollsAnOpenRequestWhateverTheTaskIsDoingMeanwhile(TaskStatus status) {
        long opened = 1_000_000_000_000L;
        TaskState task = TaskState.builder("proj", "/wt", status)
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(opened).lastPolledAt(opened).build();

        assertThat(cadence.watch(task, opened + Duration.ofMinutes(11).toMillis()).state())
                .isEqualTo(AutoReviewWatch.State.WATCHING);
    }

    @Test
    void stopsPollingATaskThatIsClosed() {
        long opened = 1_000_000_000_000L;
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DONE)
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(opened).build();

        assertThat(cadence.watch(task, opened + 1000).state()).isEqualTo(AutoReviewWatch.State.NONE);
    }

    @Test
    void timesTheWindowFromWhenTheRequestOpenedWhenNoRoundWasEverStamped() {
        long opened = 1_000_000_000_000L;
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .mrUrl("https://host/x/-/merge_requests/1").requestOpenedAt(opened).build();

        AutoReviewWatch watch = cadence.watch(task, opened + Duration.ofMinutes(5).toMillis());

        assertThat(watch.state()).isEqualTo(AutoReviewWatch.State.WATCHING);
        assertThat(watch.nextPollAt()).isEqualTo(Duration.ofMinutes(10).toMillis());
    }

    @Test
    void stopsPollingOnceTheWindowHasElapsed() {
        assertThat(cadence.pollInterval(Duration.ofHours(24).plusMinutes(1))).isNull();
    }

    @ParameterizedTest
    @CsvSource({"true,auto-review on", "false,auto-review off"})
    void summarisesWhetherAutoReviewIsOn(boolean enabled, String expected) {
        assertThat(new AutoReviewCadence(enabled, Duration.ofHours(24), 10, 60, 240).summary()).isEqualTo(expected);
    }

    @Test
    void stopsPollingATaskWhoseEveryRequestIsMerged() {
        long shipped = 1_000_000_000_000L;
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
                .mrUrl("https://host/x/-/merge_requests/1").mrCreatedAt(shipped).lastPolledAt(shipped).build()
                .withMerged("proj", true);

        assertThat(cadence.watch(task, shipped + 60_000).state()).isEqualTo(AutoReviewWatch.State.NONE);
    }
}
