package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.Owner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WatchdogServiceTest {

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "IN_PROGRESS", "SHIPPING"})
    void alertsTheHumanWhenAnAgentGoesSilentInAStatusItShouldBeWorkingIn(TaskStatus status,
                                                                        @TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", status).alias("a1")
                .lastActiveTimestamp(System.currentTimeMillis() - Duration.ofMinutes(6).toMillis()).build());
        Notifications notifications = mock(Notifications.class);

        new WatchdogService(state, notifications, properties, mock(SessionHost.class), configMock()).run();

        verify(notifications).send(argThat(sent -> sent.topic() == Notification.Topic.WATCHDOG
                && "ABC-1".equals(sent.taskId())));
    }

    @Test
    void staysQuietWhenSilentOnMcpButTheWindowIsStillPrinting(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(System.currentTimeMillis() - Duration.ofMinutes(20).toMillis()).build());
        Notifications notifications = mock(Notifications.class);
        SessionHost tmux = mock(SessionHost.class);
        when(tmux.lastWindowActivityMillis(any(), anyString())).thenReturn(System.currentTimeMillis());

        new WatchdogService(state, notifications, properties, tmux, configMock()).run();

        verifyNoInteractions(notifications);
    }

    /** Watching a status that idles by design — CI_POLLING on the host, REVIEW_PENDING on the human — is noise. */
    @Test
    void watchesOnlyTheStatusesInWhichAnAgentIsSupposedToBeWorking() {
        assertThat(Arrays.stream(TaskStatus.values()).filter(WatchdogService::watches).toList())
                .containsExactly(TaskStatus.NEW, TaskStatus.IN_PROGRESS, TaskStatus.SHIPPING);
    }

    @Test
    void staysQuietWhenTaskWaitsForHumanReview(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(System.currentTimeMillis() - Duration.ofMinutes(60).toMillis()).build());
        Notifications notifications = mock(Notifications.class);

        new WatchdogService(state, notifications, properties, mock(SessionHost.class), configMock()).run();

        verifyNoInteractions(notifications);
    }

    /**
     * A desktop ping is gone the moment it is dismissed, so the block has to be readable off the board for as
     * long as it lasts — otherwise the card keeps claiming the agent is working.
     */
    @Test
    void stampsTheSilenceOnTheTaskSoBothSurfacesShowItAndNotOnlyTheDesktopPing(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        long lastSeen = System.currentTimeMillis() - Duration.ofMinutes(6).toMillis();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(lastSeen).build());

        new WatchdogService(state, mock(Notifications.class), properties, mock(SessionHost.class),
                configMock()).run();

        assertThat(state.task("ABC-1").orElseThrow().silentSince()).isEqualTo(lastSeen);
    }

    @Test
    void takesTheStampBackOffAsSoonAsTheWindowIsPrintingAgain(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(System.currentTimeMillis() - Duration.ofMinutes(20).toMillis())
                .silentSince(1_000).build());
        SessionHost tmux = mock(SessionHost.class);
        when(tmux.lastWindowActivityMillis(any(), anyString())).thenReturn(System.currentTimeMillis());

        new WatchdogService(state, mock(Notifications.class), properties, tmux, configMock()).run();

        assertThat(state.task("ABC-1").orElseThrow().agentIsSilent()).isFalse();
    }

    /** Both surfaces repaint on a state write, and this runs once a minute against every task there is. */
    @Test
    void writesNothingOnTheTicksThatFindTheSameVerdictAsTheOneBefore(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(System.currentTimeMillis() - Duration.ofMinutes(6).toMillis()).build());
        AtomicInteger writes = new AtomicInteger();
        state.onChange(file -> writes.incrementAndGet());
        WatchdogService watchdog = new WatchdogService(state, mock(Notifications.class), properties,
                mock(SessionHost.class), configMock());

        watchdog.run();
        watchdog.run();
        watchdog.run();

        assertThat(writes).hasValue(1);
    }

    /**
     * THE rule the board rests on: a status that reads as the agent's own turn must have something watching it,
     * or a session blocked in it waits for a human who was never told.
     */
    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void watchesEveryStatusWhoseNextMoveIsTheAgentsOwn(TaskStatus status) {
        assertThat(WatchdogService.watches(status)).isEqualTo(Move.ownerOf(status) == Owner.AGENT);
    }

    private static ConfigService configMock() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        return config;
    }
}
