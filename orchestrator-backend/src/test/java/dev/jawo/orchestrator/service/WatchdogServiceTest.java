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

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WatchdogServiceTest {

    @Test
    void alertsHumanWhenAgentDiesBeforeItsFirstStatusUpdate(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.NEW,
                System.currentTimeMillis() - Duration.ofMinutes(6).toMillis(), null, "a1", null));
        UserNotifier notifier = mock(UserNotifier.class);

        new WatchdogService(state, notifier, properties).scan();

        verify(notifier).notify(eq("Orchestrator Alert"), contains("ABC-1"));
    }

    @Test
    void staysQuietWhenTaskWaitsForHumanReview(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING,
                System.currentTimeMillis() - Duration.ofMinutes(60).toMillis(), null, "a1", null));
        UserNotifier notifier = mock(UserNotifier.class);

        new WatchdogService(state, notifier, properties).scan();

        verifyNoInteractions(notifier);
    }
}
