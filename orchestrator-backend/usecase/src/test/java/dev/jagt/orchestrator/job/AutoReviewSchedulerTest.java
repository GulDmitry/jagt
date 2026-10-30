package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.AutoReviewCadence;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ReviewSweepService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AutoReviewConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AutoReviewSchedulerTest {

    private static final AutoReviewCadence CADENCE = new AutoReviewCadence(true, Duration.ofHours(24), 10, 60, 240);
    private static final long NOW = 1_000_000_000_000L;

    @Test
    void pollsWhenTheIntervalHasElapsed() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(NOW - Duration.ofMinutes(20).toMillis())
                .lastPolledAt(NOW - Duration.ofMinutes(20).toMillis()).build();

        assertThat(AutoReviewScheduler.decide(task, CADENCE, NOW)).isEqualTo(AutoReviewScheduler.Action.POLL);
    }

    @Test
    void skipsWhenTheLastPollWasTooRecent() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(NOW - Duration.ofMinutes(20).toMillis())
                .lastPolledAt(NOW - Duration.ofMinutes(2).toMillis()).build();

        assertThat(AutoReviewScheduler.decide(task, CADENCE, NOW)).isEqualTo(AutoReviewScheduler.Action.SKIP);
    }

    @Test
    void stopsPollingATaskThatHasBeenOutForReviewLongerThanTheWindow() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(NOW - Duration.ofHours(25).toMillis()).build();

        assertThat(AutoReviewScheduler.decide(task, CADENCE, NOW))
                .isEqualTo(AutoReviewScheduler.Action.WINDOW_ELAPSED);
    }

    @Test
    void skipsATaskThatOptedOutOfAutoReview() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).mrUrl("http://mr/1").autoReview(false)
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
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis())
                .lastPolledAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        Notifications notifications = mock(Notifications.class);
        long before = System.currentTimeMillis();
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));

        new AutoReviewScheduler(state, config,
                new AutoReviewPolls(sweep, notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC()).run();

        verify(sweep).sweep("ABC-1");
        assertThat(state.task("ABC-1").orElseThrow().lastPolledAt()).isBetween(before, System.currentTimeMillis());
    }

    @Test
    void tapsTheHumanOnceForARoundNobodyCouldReadInsteadOfOnlyLoggingIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis())
                .lastPolledAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        when(sweep.sweep("ABC-1")).thenReturn(new ReviewSweepService.SweepResult(
                ReviewSweepService.SweepResult.Kind.UNREADABLE, "error: read failed: http://mr/1"));
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, config,
                new AutoReviewPolls(sweep, notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC());

        scheduler.run();
        state.updateTask("ABC-1", task -> task.withLastPolledAt(0));
        scheduler.run();

        verify(sweep, times(2)).sweep("ABC-1");
        verify(notifications, times(1)).send(argThat(sent -> sent.body().contains("read failed")));
    }

    @Test
    void tapsTheHumanOncePerElapsedWindowInsteadOfPollingOn(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofHours(25).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, config,
                new AutoReviewPolls(sweep, notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC());

        scheduler.run();
        scheduler.run();

        verify(notifications).send(argThat(sent -> sent.topic() == Notification.Topic.AGENT
                && "ABC-1".equals(sent.taskId()) && sent.body().contains("past its 24h window")));
        verifyNoInteractions(sweep);
    }

    @Test
    void pingsAgainForTheNewWindowAShippedRoundOpens(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofHours(25).toMillis()).build());
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, config,
                new AutoReviewPolls(mock(ReviewSweepService.class), notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC());
        scheduler.run();

        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofHours(26).toMillis())
                .build());
        scheduler.run();

        verify(notifications, times(2)).send(
                argThat(sent -> sent.topic() == Notification.Topic.AGENT && "ABC-1".equals(sent.taskId())
                        && sent.body().contains("past its 24h window")));
    }

    @Test
    void forgetsTheRemindersOfATaskThatWasRetiredWhileStillPolling(@TempDir Path root) {
        long window = System.currentTimeMillis() - Duration.ofHours(25).toMillis();
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(window).build());
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, config,
                new AutoReviewPolls(mock(ReviewSweepService.class), notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC());
        scheduler.run();

        state.removeTask("ABC-1");
        scheduler.run();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(window).build());
        scheduler.run();

        verify(notifications, times(2)).send(
                argThat(sent -> sent.topic() == Notification.Topic.AGENT && "ABC-1".equals(sent.taskId())
                        && sent.body().contains("past its 24h window")));
    }

    @Test
    void keepsPollingARoundThatCameBackCleanUntilSomebodyApprovesIt(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(61).toMillis())
                .lastPolledAt(System.currentTimeMillis() - Duration.ofMinutes(61).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));

        new AutoReviewScheduler(state, config,
                new AutoReviewPolls(sweep, mock(Notifications.class), new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), mock(Notifications.class), Clock.systemUTC()).run();

        verify(sweep).sweep("ABC-1");
    }

    @Test
    void pollsNothingWhenTheInstallHasAutoReviewOff(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofHours(1).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        Notifications notifications = mock(Notifications.class);
        ConfigService disabled = mock(ConfigService.class);
        when(disabled.load()).thenReturn(ConfigFile.defaults());

        new AutoReviewScheduler(state, disabled,
                new AutoReviewPolls(sweep, notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC()).run();

        verifyNoInteractions(sweep, notifications);
    }

    @Test
    void sweepsNothingWhereTheTaskMadeTwentyUnattendedMovesInTheHour(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        long now = System.currentTimeMillis();
        List<StatusChange> moves = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> new StatusChange(TaskStatus.CI_POLLING, now - 1_000L - i, ActionOrigin.AUTO_REVIEW)).toList();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true).history(moves)
                .mrCreatedAt(now - Duration.ofMinutes(30).toMillis())
                .lastPolledAt(now - Duration.ofMinutes(30).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));

        new AutoReviewScheduler(state, config,
                new AutoReviewPolls(sweep, notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC()).run();

        verify(sweep, never()).sweep("ABC-1");
    }

    @Test
    void pollsTheMomentTheMinimumIntervalHasExactlyElapsed() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(NOW)
                .lastPolledAt(NOW - Duration.ofMinutes(10).toMillis()).build();

        assertThat(AutoReviewScheduler.decide(task, CADENCE, NOW)).isEqualTo(AutoReviewScheduler.Action.POLL);
    }

    @Test
    void tapsTheHumanAtTheFirstUnreadableRound(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis())
                .lastPolledAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        when(sweep.sweep("ABC-1")).thenReturn(new ReviewSweepService.SweepResult(
                ReviewSweepService.SweepResult.Kind.UNREADABLE, "error: read failed: http://mr/1"));
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));

        new AutoReviewScheduler(state, config,
                new AutoReviewPolls(sweep, notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC()).run();

        verify(notifications).send(argThat(sent -> sent.body().contains("read failed")));
    }

    @Test
    void pingsAgainForARoundWhoseTaskLeftAutoReviewAndCameBack(@TempDir Path root) {
        long window = System.currentTimeMillis() - Duration.ofHours(25).toMillis();
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true).mrCreatedAt(window).build());
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, config,
                new AutoReviewPolls(mock(ReviewSweepService.class), notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC());
        scheduler.run();

        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE)
                .mrUrl("http://mr/1").autoReview(true).mrCreatedAt(window).build());
        scheduler.run();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true).mrCreatedAt(window).build());
        scheduler.run();

        verify(notifications, times(2)).send(
                argThat(sent -> sent.topic() == Notification.Topic.AGENT && "ABC-1".equals(sent.taskId())));
    }

    @Test
    void tapsOnceForAnElapsedWindowWhileAnotherTaskIsClosed(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofHours(25).toMillis()).build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.DONE).mrUrl("http://mr/2").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).build());
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, config,
                new AutoReviewPolls(mock(ReviewSweepService.class), notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC());

        scheduler.run();
        scheduler.run();

        verify(notifications, times(1)).send(argThat(sent -> "ABC-1".equals(sent.taskId())));
    }

    @Test
    void tapsOnceForAnUnreadableRoundWhileAnotherTaskIsClosed(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis())
                .lastPolledAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.DONE).mrUrl("http://mr/2").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        when(sweep.sweep("ABC-1")).thenReturn(new ReviewSweepService.SweepResult(
                ReviewSweepService.SweepResult.Kind.UNREADABLE, "error: read failed: http://mr/1"));
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, config,
                new AutoReviewPolls(sweep, notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC());

        scheduler.run();
        state.updateTask("ABC-1", task -> task.withLastPolledAt(0));
        scheduler.run();

        verify(notifications, times(1)).send(argThat(sent -> sent.body().contains("read failed")));
    }

    @Test
    void tapsAgainForAnUnreadableRoundWhoseTaskWasClosedAndReopened(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState polling = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis())
                .lastPolledAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).build();
        state.putTask("ABC-1", polling);
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        when(sweep.sweep("ABC-1")).thenReturn(new ReviewSweepService.SweepResult(
                ReviewSweepService.SweepResult.Kind.UNREADABLE, "error: read failed: http://mr/1"));
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, config,
                new AutoReviewPolls(sweep, notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC());
        scheduler.run();

        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE)
                .mrUrl("http://mr/1").autoReview(true).mrCreatedAt(polling.mrCreatedAt()).build());
        scheduler.run();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true).mrCreatedAt(polling.mrCreatedAt()).lastPolledAt(1).build());
        scheduler.run();

        verify(notifications, times(2)).send(argThat(sent -> sent.body().contains("read failed")));
    }

    @Test
    void stampsThePollAtTheInstantTheClockGave(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true).mrCreatedAt(999_998_200_000L)
                .lastPolledAt(999_998_200_000L).build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000_000_000_000L), ZoneOffset.UTC);

        new AutoReviewScheduler(state, config, new AutoReviewPolls(mock(ReviewSweepService.class), mock(Notifications.class), new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), mock(Notifications.class), clock).run();

        assertThat(state.task("ABC-1").orElseThrow().lastPolledAt()).isEqualTo(1_000_000_000_000L);
    }

    @Test
    void submitsATaskOnceWhileItsEarlierPollIsStillRunning(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis())
                .lastPolledAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        List<Runnable> queued = new ArrayList<>();
        Notifications notifications = mock(Notifications.class);
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, config, new AutoReviewPolls(
                mock(ReviewSweepService.class), notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), queued::add, state), notifications, Clock.systemUTC());

        scheduler.run();
        scheduler.run();

        assertThat(queued).hasSize(1);
    }

    @Test
    void tapsAgainForAnUnreadableRoundWhoseTaskWasRetiredAndCameBack(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        TaskState polling = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis())
                .lastPolledAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).build();
        state.putTask("ABC-1", polling);
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        when(sweep.sweep("ABC-1")).thenReturn(new ReviewSweepService.SweepResult(
                ReviewSweepService.SweepResult.Kind.UNREADABLE, "error: read failed: http://mr/1"));
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, config,
                new AutoReviewPolls(sweep, notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC());
        scheduler.run();

        state.removeTask("ABC-1");
        scheduler.run();
        state.putTask("ABC-1", polling);
        scheduler.run();

        verify(notifications, times(2)).send(argThat(sent -> sent.body().contains("read failed")));
    }

    @Test
    void doesNotTapAgainAfterARestartForAnUnreadableRoundAlreadyTold(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis())
                .lastPolledAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        when(sweep.sweep("ABC-1")).thenReturn(new ReviewSweepService.SweepResult(
                ReviewSweepService.SweepResult.Kind.UNREADABLE, "error: read failed: http://mr/1"));
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        new AutoReviewScheduler(state, config,
                new AutoReviewPolls(sweep, notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC()).run();
        state.updateTask("ABC-1", task -> task.withLastPolledAt(0));

        new AutoReviewScheduler(state, config,
                new AutoReviewPolls(sweep, notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC()).run();

        verify(notifications, times(1)).send(argThat(sent -> sent.body().contains("read failed")));
    }

    @Test
    void doesNotTapAgainAfterARestartForAnElapsedWindowAlreadyTold(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .mrUrl("http://mr/1").autoReview(true)
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofHours(25).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigFile.defaults()
                .withAutoReview(AutoReviewConfig.defaults().withEnabled(true)));
        new AutoReviewScheduler(state, config,
                new AutoReviewPolls(sweep, notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC()).run();

        new AutoReviewScheduler(state, config,
                new AutoReviewPolls(sweep, notifications, new TaskStops(state, ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(
                                OrchestratorProperties.defaults().withRoot(root.toString())
                                        .withStateFile(root.resolve("state.json").toString())))), Clock.systemUTC()), Runnable::run, state), notifications, Clock.systemUTC()).run();

        verify(notifications, times(1)).send(any(Notification.class));
    }
}
