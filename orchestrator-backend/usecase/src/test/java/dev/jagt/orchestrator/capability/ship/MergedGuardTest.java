package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.MergedRequests;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MergedGuardTest {

    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(100_000_000), ZoneOffset.UTC);
    private final MergedRequests merged = mock(MergedRequests.class);

    @Test
    void doesNotCountAShipPressedAfterTheRequestMergedTowardTheLoopStop(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .history(List.of(new StatusChange(TaskStatus.SHIPPING, 99_000_000, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.SHIPPING, 99_100_000, ActionOrigin.MASTER),
                        new StatusChange(TaskStatus.SHIPPING, 99_200_000, ActionOrigin.MASTER))).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(mock(Notifications.class), new StopLog(new JsonMapper(), paths)), clock);
        when(merged.refresh("ABC-42")).thenReturn(true);

        new MergedGuard(merged).around("ABC-42", () -> {
            state.updateTask("ABC-42", task -> task.withStatus(TaskStatus.SHIPPING, "shipping", true, clock.millis()));
            return Outcome.relayed("relayed", "shipping");
        });

        assertThat(stops.mayShip("ABC-42")).isTrue();
    }

    @Test
    void runsTheShipWhereARequestIsStillOpen() {
        when(merged.refresh("ABC-42")).thenReturn(false);

        Outcome outcome = new MergedGuard(merged).around("ABC-42", () -> Outcome.relayed("relayed", "shipping"));

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
    }

    @Test
    void shipsNothingWhereEveryRequestIsMerged() {
        when(merged.refresh("ABC-42")).thenReturn(true);

        Outcome outcome = new MergedGuard(merged).around("ABC-42", () -> Outcome.relayed("relayed", "shipping"));

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.NOTHING);
    }
}
