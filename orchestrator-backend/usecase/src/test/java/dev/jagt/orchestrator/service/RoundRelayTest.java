package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoundRelayTest {

    private final AgentSessions sessions = mock(AgentSessions.class);
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000_000), ZoneOffset.UTC);

    @Test
    void relaysNoRoundIdenticalToTheLastOneWhenAnotherInstructionOverwroteTheContextFileBetween(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        CaseDoor door = new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, clock),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), clock));
        RoundRelay relay = new RoundRelay(new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), clock),
                new StepRelay(new SessionBrief(paths), sessions, door,
                        new TaskTurns(state, clock)), door, new SessionBrief(paths));
        ReviewFacts round = new ReviewFacts(true, false, "running", "", List.of("https://host/mr/1#note_1\nbot: rename x"), 0, List.of());
        relay.relay("ABC-42", "https://host/mr/1", round, "running");

        SweepResult again = relay.relay("ABC-42", "https://host/mr/1", round, "running");

        assertThat(again.kind()).isEqualTo(SweepResult.Kind.UNCHANGED);
        verify(sessions, times(1)).relayFresh(eq("ABC-42"), anyString());
    }

    @Test
    void relaysNothingIntoATaskJagtStopped(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .stop(new TaskStop(TaskStop.Trigger.SHIPS, 1L, "5 ships")).build());
        CaseDoor door = new CaseDoor(new SessionCases(paths), new MasterCases(paths), state,
                new MissLog(new JsonMapper(), paths, clock),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), clock));
        RoundRelay relay = new RoundRelay(new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), clock),
                new StepRelay(new SessionBrief(paths), sessions, door,
                        new TaskTurns(state, clock)), door, new SessionBrief(paths));

        SweepResult result = relay.relay("ABC-42", "https://host/mr/1",
                new ReviewFacts(true, false, "running", "", List.of("https://host/mr/1#note_9\nlee: why?"), 0, List.of()), "running");

        assertThat(result.kind()).isEqualTo(SweepResult.Kind.STOPPED);
        verify(sessions, never()).relayFresh(anyString(), anyString());
    }

    @Test
    void relaysTheThreadsAsDraftsAndCountsThem(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-brief.md.dist"), root.resolve("session-brief.md.dist"));
        TaskStops stops = mock(TaskStops.class);
        when(stops.round(anyString(), any(), anyString())).thenReturn(TaskStops.Relay.RELAY);
        StateService state = new StateService(new JsonMapper(), paths);

        CaseDoor door = new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, clock),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), clock));

        SweepResult result = new RoundRelay(stops, new StepRelay(new SessionBrief(paths), sessions, door,
                new TaskTurns(state, clock)), door, new SessionBrief(paths)).relay("ABC-42", "http://mr/api, http://mr/web",
                new ReviewFacts(true, false, "success", "", List.of("[api] http://mr/api#note_1", "[web] http://mr/web#note_2"),
                        0, List.of()), "success");

        assertThat(result.message()).contains("2 thread(s) relayed");
        verify(sessions).relayFresh(eq("ABC-42"), contains("review_replies.md"));
    }

    @Test
    void reportsARoundUnchangedWhileTheSessionHoldsTheTurnItOpened(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        TaskStops stops = mock(TaskStops.class);
        when(stops.round(anyString(), any(), anyString())).thenReturn(TaskStops.Relay.RELAY);
        CaseDoor door = new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, clock),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), clock));
        RoundRelay relay = new RoundRelay(stops, new StepRelay(new SessionBrief(paths), sessions, door,
                new TaskTurns(state, clock)), door, new SessionBrief(paths));
        ReviewFacts round = new ReviewFacts(true, false, "success", "", List.of("reviewer (a.java:3): drop the cache"), 0, List.of());
        relay.relay("ABC-42", "http://mr/1", round, "success");

        SweepResult again = relay.relay("ABC-42", "http://mr/1", round, "success");

        assertThat(again.kind()).isEqualTo(SweepResult.Kind.UNCHANGED);
        assertThat(again.message()).contains("unchanged since the last relay");
    }

    @Test
    void relaysNoRoundWhereNoCaseSendsItsStepsAtTheTasksStatus(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DONE).build());
        TaskStops stops = mock(TaskStops.class);
        CaseDoor door = new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, clock),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new Notifications(List.of(), Runnable::run), clock));
        RoundRelay relay = new RoundRelay(stops, new StepRelay(new SessionBrief(paths), sessions, door,
                new TaskTurns(state, clock)), door, new SessionBrief(paths));

        SweepResult result = relay.relay("ABC-42", "https://host/mr/1",
                new ReviewFacts(true, false, "running", "", List.of("https://host/mr/1#note_9\nlee: why?"), 0, List.of()), "running");

        assertThat(result.kind()).isEqualTo(SweepResult.Kind.REFUSED);
        verify(sessions, never()).relayFresh(anyString(), anyString());
    }
}
