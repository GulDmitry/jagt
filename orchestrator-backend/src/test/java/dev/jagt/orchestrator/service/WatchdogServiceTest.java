package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.notify.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WatchdogServiceTest {

    @Test
    void alertsHumanWhenAgentDiesBeforeItsFirstStatusUpdate(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW)
                .lastActiveTimestamp(System.currentTimeMillis() - Duration.ofMinutes(6).toMillis()).alias("a1").build());
        Notifications notifications = mock(Notifications.class);
        TmuxService tmux = mock(TmuxService.class);
        ConfigService config = configMock();

        new WatchdogService(state, notifications, properties, tmux, config).run();

        verify(notifications).send(argThat(sent -> sent.topic() == Notification.Topic.WATCHDOG
                && "ABC-1".equals(sent.taskId())));
    }

    @Test
    void staysQuietWhenSilentOnMcpButTheWindowIsStillPrinting(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(System.currentTimeMillis() - Duration.ofMinutes(20).toMillis()).alias("a1").build());
        Notifications notifications = mock(Notifications.class);
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.lastWindowActivityMillis(any(), anyString())).thenReturn(System.currentTimeMillis());

        new WatchdogService(state, notifications, properties, tmux, configMock()).run();

        verifyNoInteractions(notifications);
    }

    private static ConfigService configMock() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        return config;
    }

    @Test
    void alertsWhenTheAgentDiesMidShip(@TempDir Path root) {
        // The documented "stuck at SHIPPING, no MR appears" failure: the agent crashed after `ship` relayed
        // the approval. It used to be invisible to the watchdog, so recovery waited on the human noticing.
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.SHIPPING)
                .lastActiveTimestamp(System.currentTimeMillis() - Duration.ofMinutes(6).toMillis()).alias("a1").build());
        Notifications notifications = mock(Notifications.class);

        new WatchdogService(state, notifications, properties, mock(TmuxService.class), configMock()).run();

        verify(notifications).send(argThat(sent -> sent.topic() == Notification.Topic.WATCHDOG
                && "ABC-1".equals(sent.taskId())));
    }

    @Test
    void watchesOnlyTheStatusesInWhichAnAgentIsSupposedToBeWorking() {
        // The negative half matters as much as the positive one: watching a status that idles by design
        // (CI_POLLING waits on the code host, REVIEW_PENDING on the human) turns the alert into noise.
        assertThat(Arrays.stream(TaskStatus.values()).filter(WatchdogService::watches).toList())
                .containsExactly(TaskStatus.NEW, TaskStatus.IN_PROGRESS, TaskStatus.SHIPPING);
    }

    @Test
    void staysQuietWhenTaskWaitsForHumanReview(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .lastActiveTimestamp(System.currentTimeMillis() - Duration.ofMinutes(60).toMillis()).alias("a1").build());
        Notifications notifications = mock(Notifications.class);

        new WatchdogService(state, notifications, properties, mock(TmuxService.class), configMock()).run();

        verifyNoInteractions(notifications);
    }
}
