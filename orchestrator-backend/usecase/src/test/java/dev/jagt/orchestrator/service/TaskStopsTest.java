package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.RoundTrail;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.ReviewFacts;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TaskStopsTest {

    private final Notifications notifications = mock(Notifications.class);
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(100_000_000), ZoneOffset.UTC);

    @Test
    void stopsATaskWhoseRoundsResolveNothingAndTellsTheHumanOnce(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, notifications, clock);
        ReviewFacts round = new ReviewFacts(true, false, "failed", "unit-tests\nCartTest failed",
                List.of("https://host/mr/1#note_1", "https://host/mr/1#note_2"), 0, List.of());
        stops.round("ABC-42", round, "brief 1");
        stops.round("ABC-42", round, "brief 2");
        stops.round("ABC-42", round, "brief 3");

        TaskStops.Relay fourth = stops.round("ABC-42", round, "brief 4");
        TaskStops.Relay fifth = stops.round("ABC-42", round, "brief 5");

        assertThat(fourth).isEqualTo(TaskStops.Relay.STOPPED);
        assertThat(fifth).isEqualTo(TaskStops.Relay.STOPPED);
        assertThat(state.task("ABC-42").orElseThrow().stop())
                .extracting(TaskStop::trigger).isEqualTo(TaskStop.Trigger.STALLED);
        verify(notifications, times(1)).send(any());
    }

    @Test
    void refusesTheFifthUnaskedShipWithinTheWindow(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).history(List.of(
                new StatusChange(TaskStatus.SHIPPING, 99_000_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 99_200_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 99_400_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.SHIPPING, 99_600_000, ActionOrigin.MASTER),
                new StatusChange(TaskStatus.REVIEW_PENDING, 99_800_000, ActionOrigin.MCP))).build());

        boolean may = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, notifications, clock).mayShip("ABC-42");

        assertThat(may).isFalse();
        assertThat(state.task("ABC-42").orElseThrow().stop())
                .extracting(TaskStop::trigger).isEqualTo(TaskStop.Trigger.SHIPS);
    }

    @Test
    void relaysTheStoppedRoundAgainOnceTheHumanLiftsTheStop(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .trail(RoundTrail.none().withRound(new RoundTrail.Round(1, 2, "", "same")))
                .stop(new TaskStop(TaskStop.Trigger.STALLED, 2, "3 rounds")).build());
        TaskStops stops = new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, notifications, clock);

        stops.lift("ABC-42");

        assertThat(stops.round("ABC-42", new ReviewFacts(true, false, "running", "", List.of("x"), 0, List.of()), "same"))
                .isEqualTo(TaskStops.Relay.RELAY);
    }
}
