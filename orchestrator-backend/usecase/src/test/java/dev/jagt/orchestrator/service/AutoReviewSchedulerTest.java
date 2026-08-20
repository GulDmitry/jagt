package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AutoReviewConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AutoReviewSchedulerTest {

    private static final AutoReviewCadence CADENCE = new AutoReviewCadence(true, Duration.ofHours(24), 10, 60);
    private static final long NOW = 1_000_000_000_000L;

    private static TaskState.Builder polling() {
        return TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).mrUrl("http://mr/1").autoReview(true);
    }

    @Test
    void pollsWhenTheIntervalHasElapsed() {
        TaskState task = polling()
                .mrCreatedAt(NOW - Duration.ofMinutes(20).toMillis())
                .lastPolledAt(NOW - Duration.ofMinutes(20).toMillis()).build();

        assertThat(AutoReviewScheduler.decide(task, CADENCE, NOW)).isEqualTo(AutoReviewScheduler.Action.POLL);
    }

    @Test
    void skipsWhenTheLastPollWasTooRecent() {
        TaskState task = polling()
                .mrCreatedAt(NOW - Duration.ofMinutes(20).toMillis())
                .lastPolledAt(NOW - Duration.ofMinutes(2).toMillis()).build();

        assertThat(AutoReviewScheduler.decide(task, CADENCE, NOW)).isEqualTo(AutoReviewScheduler.Action.SKIP);
    }

    @Test
    void stopsPollingATaskThatHasBeenOutForReviewLongerThanTheWindow() {
        TaskState task = polling().mrCreatedAt(NOW - Duration.ofHours(25).toMillis()).build();

        assertThat(AutoReviewScheduler.decide(task, CADENCE, NOW))
                .isEqualTo(AutoReviewScheduler.Action.WINDOW_ELAPSED);
    }

    @Test
    void skipsATaskThatOptedOutOfAutoReview() {
        TaskState task = polling().autoReview(false)
                .mrCreatedAt(NOW - Duration.ofMinutes(20).toMillis()).build();

        assertThat(AutoReviewScheduler.decide(task, CADENCE, NOW)).isEqualTo(AutoReviewScheduler.Action.SKIP);
    }

    @Test
    void skipsATaskWithNoRequestToRead() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).autoReview(true)
                .mrCreatedAt(NOW - Duration.ofMinutes(20).toMillis()).build();

        assertThat(AutoReviewScheduler.decide(task, CADENCE, NOW)).isEqualTo(AutoReviewScheduler.Action.SKIP);
    }

    @Test
    void sweepsADueTaskAndRecordsThatItLooked(@TempDir Path root) {
        StateService state = stateWith(root, polling()
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis())
                .lastPolledAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        Notifications notifications = mock(Notifications.class);

        new AutoReviewScheduler(state, enabledConfig(), sweep, notifications, Runnable::run).run();

        verify(sweep).sweep("ABC-1");
        assertThat(state.task("ABC-1").orElseThrow().lastPolledAt())
                .isGreaterThan(System.currentTimeMillis() - Duration.ofMinutes(1).toMillis());
    }

    @Test
    void tapsTheHumanOncePerElapsedWindowInsteadOfPollingOn(@TempDir Path root) {
        StateService state = stateWith(root, polling()
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofHours(25).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        Notifications notifications = mock(Notifications.class);
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, enabledConfig(), sweep,
                notifications, Runnable::run);

        scheduler.run();
        scheduler.run();

        verify(notifications).send(argThat(sent -> sent.topic() == Notification.Topic.AGENT
                && "ABC-1".equals(sent.taskId()) && sent.body().contains("past its 24h window")));
        verifyNoInteractions(sweep);
    }

    /** A round shipped in-process never leaves CI_POLLING, so a per-task marker would silence it forever. */
    @Test
    void pingsAgainForTheNewWindowAShippedRoundOpens(@TempDir Path root) {
        StateService state = stateWith(root, polling()
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofHours(25).toMillis()).build());
        Notifications notifications = mock(Notifications.class);
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, enabledConfig(),
                mock(ReviewSweepService.class), notifications, Runnable::run);
        scheduler.run();

        state.updateTask("ABC-1", task -> task.withReviewRound("http://mr/1"));
        state.updateTask("ABC-1", task -> task.withMrCreatedAt(
                System.currentTimeMillis() - Duration.ofHours(25).toMillis()));
        scheduler.run();

        verify(notifications, times(2)).send(
                argThat(sent -> sent.topic() == Notification.Topic.AGENT && "ABC-1".equals(sent.taskId())
                        && sent.body().contains("past its 24h window")));
    }

    /**
     * A task retired with `done` while still CI_POLLING never LEAVES that status, so the per-window marker had
     * nothing to clear it: the set grew by one string per such task for the life of the process. Asserted the
     * only way it is observable from outside — a task that comes back under the same id and window gets its
     * reminder again instead of being silently treated as already-notified.
     */
    @Test
    void forgetsTheRemindersOfATaskThatWasRetiredWhileStillPolling(@TempDir Path root) {
        long window = System.currentTimeMillis() - Duration.ofHours(25).toMillis();
        StateService state = stateWith(root, polling().mrCreatedAt(window).build());
        Notifications notifications = mock(Notifications.class);
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, enabledConfig(),
                mock(ReviewSweepService.class), notifications, Runnable::run);
        scheduler.run();

        state.removeTask("ABC-1");
        scheduler.run();
        state.putTask("ABC-1", polling().mrCreatedAt(window).build());
        scheduler.run();

        verify(notifications, times(2)).send(
                argThat(sent -> sent.topic() == Notification.Topic.AGENT && "ABC-1".equals(sent.taskId())
                        && sent.body().contains("past its 24h window")));
    }

    /**
     * An approval lands after the round already came back clean, and REVIEWED is where that round sits — a poll
     * that stopped there would never see one, which is the one thing the human is told about without asking.
     */
    @Test
    void keepsPollingARoundThatCameBackCleanUntilSomebodyApprovesIt(@TempDir Path root) {
        StateService state = stateWith(root, polling().status(TaskStatus.REVIEWED)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis())
                .lastPolledAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);

        new AutoReviewScheduler(state, enabledConfig(), sweep, mock(Notifications.class), Runnable::run).run();

        verify(sweep).sweep("ABC-1");
    }

    @Test
    void pollsNothingWhenTheInstallHasAutoReviewOff(@TempDir Path root) {
        StateService state = stateWith(root, polling()
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofHours(1).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        Notifications notifications = mock(Notifications.class);
        ConfigService disabled = mock(ConfigService.class);
        when(disabled.load()).thenReturn(ConfigFile.defaults());

        new AutoReviewScheduler(state, disabled, sweep, notifications, Runnable::run).run();

        verifyNoInteractions(sweep, notifications);
    }


    private static StateService stateWith(Path root, TaskState task) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", task);
        return state;
    }

    private static ConfigService enabledConfig() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        return config;
    }
}
