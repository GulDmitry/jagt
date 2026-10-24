package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.LoopWatch;
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
import static org.mockito.Mockito.when;

class LoopGuardTest {

    private final ConfigService config = mock(ConfigService.class);
    private final Notifications notifications = mock(Notifications.class);
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(100_000_000), ZoneOffset.UTC);

    @Test
    void stopsATaskWhoseRoundsResolveNothingAndTellsTheHumanOnce(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        LoopGuard loops = new LoopGuard(state, config, new TaskStops(state, notifications, clock), clock);
        ReviewFacts round = new ReviewFacts(true, false, "failed", "unit-tests\nCartTest failed",
                List.of("https://host/mr/1#note_1", "https://host/mr/1#note_2"), 0, List.of());
        loops.round("ABC-42", round, "brief 1");
        loops.round("ABC-42", round, "brief 2");
        loops.round("ABC-42", round, "brief 3");

        LoopGuard.Relay fourth = loops.round("ABC-42", round, "brief 4");
        LoopGuard.Relay fifth = loops.round("ABC-42", round, "brief 5");

        assertThat(fourth).isEqualTo(LoopGuard.Relay.STOPPED);
        assertThat(fifth).isEqualTo(LoopGuard.Relay.STOPPED);
        assertThat(state.task("ABC-42").orElseThrow().loopOrNone().stop())
                .extracting(LoopWatch.Stop::trigger).isEqualTo(LoopWatch.Trigger.STALLED);
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
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

        boolean may = new LoopGuard(state, config, new TaskStops(state, notifications, clock), clock).mayShip("ABC-42");

        assertThat(may).isFalse();
        assertThat(state.task("ABC-42").orElseThrow().loopOrNone().stop())
                .extracting(LoopWatch.Stop::trigger).isEqualTo(LoopWatch.Trigger.SHIPS);
    }

    @Test
    void relaysTheStoppedRoundAgainOnceTheHumanLiftsTheStop(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .loop(LoopWatch.none().withRound(new LoopWatch.Round(1, 2, "", "same"))
                        .stopped(new LoopWatch.Stop(LoopWatch.Trigger.STALLED, 2, "3 rounds"))).build());
        LoopGuard loops = new LoopGuard(state, config, new TaskStops(state, notifications, clock), clock);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());

        loops.lift("ABC-42");

        assertThat(loops.round("ABC-42", new ReviewFacts(true, false, "running", "", List.of("x"), 0, List.of()), "same"))
                .isEqualTo(LoopGuard.Relay.RELAY);
    }
}
