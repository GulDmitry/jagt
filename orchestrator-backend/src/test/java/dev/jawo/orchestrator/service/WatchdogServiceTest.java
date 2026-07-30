package dev.jawo.orchestrator.service;

import dev.jawo.orchestrator.config.OrchestratorPaths;
import dev.jawo.orchestrator.config.OrchestratorProperties;
import dev.jawo.orchestrator.model.TaskState;
import dev.jawo.orchestrator.model.TaskStatus;
import dev.jawo.orchestrator.platform.UserNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WatchdogServiceTest {

    @Test
    void alertsHumanWhenAgentDiesBeforeItsFirstStatusUpdate(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.NEW,
                System.currentTimeMillis() - Duration.ofMinutes(6).toMillis(), null, "a1", null, null, null));
        UserNotifier notifier = mock(UserNotifier.class);
        TmuxService tmux = mock(TmuxService.class);
        ConfigService config = configMock();

        new WatchdogService(state, notifier, properties, tmux, config).scan();

        verify(notifier).notify(eq("Orchestrator Alert"), contains("ABC-1"));
    }

    @Test
    void staysQuietWhenSilentOnMcpButTheWindowIsStillPrinting(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.IN_PROGRESS,
                System.currentTimeMillis() - Duration.ofMinutes(20).toMillis(), null, "a1", null, null, null));
        UserNotifier notifier = mock(UserNotifier.class);
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.lastWindowActivityMillis(any(), anyString())).thenReturn(System.currentTimeMillis());

        new WatchdogService(state, notifier, properties, tmux, configMock()).scan();

        verifyNoInteractions(notifier);
    }

    private static ConfigService configMock() {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(new ConfigService.ConfigFile(Map.of(), null, null, null, null, null, null, null, null));
        return config;
    }

    @Test
    void staysQuietWhenTaskWaitsForHumanReview(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING,
                System.currentTimeMillis() - Duration.ofMinutes(60).toMillis(), null, "a1", null, null, null));
        UserNotifier notifier = mock(UserNotifier.class);

        new WatchdogService(state, notifier, properties, mock(TmuxService.class), configMock()).scan();

        verifyNoInteractions(notifier);
    }
}
