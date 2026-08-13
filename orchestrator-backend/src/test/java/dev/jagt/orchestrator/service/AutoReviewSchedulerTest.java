package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.UserNotifier;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.AutoReviewConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AutoReviewSchedulerTest {

    private static final AutoReviewCadence CADENCE = new AutoReviewCadence(Duration.ofHours(24), 10, 60);
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
    void reportsWindowElapsedPastTheWindow() {
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
    void skipsWhenNoMrIsLinkedYet() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).autoReview(true)
                .mrCreatedAt(NOW - Duration.ofMinutes(20).toMillis()).build();

        assertThat(AutoReviewScheduler.decide(task, CADENCE, NOW)).isEqualTo(AutoReviewScheduler.Action.SKIP);
    }

    @Test
    void scanSweepsADueTaskAndRecordsThePoll(@TempDir Path root) {
        StateService state = stateWith(root, polling()
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis())
                .lastPolledAt(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        UserNotifier notifier = mock(UserNotifier.class);

        new AutoReviewScheduler(state, enabledConfig(), sweep, notifier, Runnable::run).scan();

        verify(sweep).sweep("ABC-1");
        assertThat(state.task("ABC-1").orElseThrow().lastPolledAt())
                .isGreaterThan(System.currentTimeMillis() - Duration.ofMinutes(1).toMillis());
    }

    @Test
    void scanPingsOncePerElapsedWindowAndDoesNotPoll(@TempDir Path root) {
        StateService state = stateWith(root, polling()
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofHours(25).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        UserNotifier notifier = mock(UserNotifier.class);
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, enabledConfig(), sweep, notifier, Runnable::run);

        scheduler.scan();
        scheduler.scan();

        verify(notifier).notify(eq("jagt · ABC-1"), contains("window elapsed"));
        verifyNoInteractions(sweep);
    }

    @Test
    void pingsAgainForTheNewWindowAShippedRoundOpens(@TempDir Path root) {
        // A round shipped in-process never leaves CI_POLLING, so the old per-task marker silenced the reminder
        // for the rest of the task's life — and the new pipeline was never polled either.
        StateService state = stateWith(root, polling()
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofHours(25).toMillis()).build());
        UserNotifier notifier = mock(UserNotifier.class);
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, enabledConfig(),
                mock(ReviewSweepService.class), notifier, Runnable::run);
        scheduler.scan();

        // ship lands another round: same status, brand-new window
        state.updateTask("ABC-1", task -> task.withReviewRound("http://mr/1"));
        state.updateTask("ABC-1", task -> task.withMrCreatedAt(
                System.currentTimeMillis() - Duration.ofHours(25).toMillis()));
        scheduler.scan();

        verify(notifier, org.mockito.Mockito.times(2)).notify(eq("jagt · ABC-1"), contains("window elapsed"));
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
        UserNotifier notifier = mock(UserNotifier.class);
        AutoReviewScheduler scheduler = new AutoReviewScheduler(state, enabledConfig(),
                mock(ReviewSweepService.class), notifier, Runnable::run);
        scheduler.scan();

        state.removeTask("ABC-1");
        scheduler.scan();                                        // nothing to notify, and the marker is dropped
        state.putTask("ABC-1", polling().mrCreatedAt(window).build());
        scheduler.scan();

        verify(notifier, org.mockito.Mockito.times(2)).notify(eq("jagt · ABC-1"), contains("window elapsed"));
    }

    @Test
    void scanDoesNothingWhenAutoReviewIsDisabled(@TempDir Path root) {
        StateService state = stateWith(root, polling()
                .mrCreatedAt(System.currentTimeMillis() - Duration.ofHours(1).toMillis()).build());
        ReviewSweepService sweep = mock(ReviewSweepService.class);
        UserNotifier notifier = mock(UserNotifier.class);
        ConfigService disabled = mock(ConfigService.class);
        when(disabled.load()).thenReturn(ConfigFile.defaults());

        new AutoReviewScheduler(state, disabled, sweep, notifier, Runnable::run).scan();

        verifyNoInteractions(sweep, notifier);
    }

    @Test
    void marksExactlyOneConstructorForSpringSoTheContextCanInstantiateIt() {
        // Two constructors (the injected one + the test one that takes an Executor) → Spring needs @Autowired
        // on exactly one, else it demands a no-arg default and the whole app fails to start.
        long autowired = Arrays.stream(AutoReviewScheduler.class.getDeclaredConstructors())
                .filter(c -> c.isAnnotationPresent(Autowired.class))
                .count();
        Constructor<?>[] all = AutoReviewScheduler.class.getDeclaredConstructors();

        assertThat(all.length).isGreaterThan(1);   // guard only matters while there IS ambiguity
        assertThat(autowired).isEqualTo(1);
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
