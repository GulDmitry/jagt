package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.MissLog;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import tools.jackson.databind.json.JsonMapper;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.task.ConflictWatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConflictHandOffTest {

    private final AgentSessions sessions = mock(AgentSessions.class);
    private final ConflictStall stall = mock(ConflictStall.class);
    private final ConflictFinish finish = mock(ConflictFinish.class);

    @Test
    void handsAConflictToTheSessionAsAWorktreeOfItsOwn(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        state.putTask("ABC-42", TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT).build());

        new ConflictHandOff(sessions, new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC()))), stall, finish).handle("ABC-42", new DeployConflicts.WaitingConflict(
                Path.of("/src/ABC-42-deploy"), false, new ConflictWatch.Progress(7, 0, "d")));

        verify(sessions).admit("ABC-42", Path.of("/src/ABC-42-deploy"));
        verify(sessions).writeTaskContext(eq("ABC-42"), contains("/src/ABC-42-deploy"));
    }

    @Test
    void handsAConflictOnlyOnce(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        when(stall.handed("ABC-42")).thenReturn(true);

        new ConflictHandOff(sessions, new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC()))), stall, finish).handle("ABC-42", new DeployConflicts.WaitingConflict(
                Path.of("/src/ABC-42-deploy"), false, new ConflictWatch.Progress(7, 0, "d")));

        verify(sessions, never()).writeTaskContext(eq("ABC-42"), anyString());
    }

    @Test
    void watchesAHandedConflictForProgress(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        DeployConflicts.WaitingConflict conflict = new DeployConflicts.WaitingConflict(Path.of("/src/ABC-42-deploy"),
                false, new ConflictWatch.Progress(7, 0, "d"));
        when(stall.handed("ABC-42")).thenReturn(true);

        new ConflictHandOff(sessions, new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC()))), stall, finish).handle("ABC-42", conflict);

        verify(stall).watch("ABC-42", conflict);
    }

    @Test
    void finishesTheDeployOnceTheResolutionIsStagedInFull(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        new ConflictHandOff(sessions, new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(state, new Notifications(List.of(), Runnable::run), Clock.systemUTC()))), stall, finish).handle("ABC-42", new DeployConflicts.WaitingConflict(
                Path.of("/src/ABC-42-deploy"), true, new ConflictWatch.Progress(0, 7, "d")));

        verify(finish).deploy("ABC-42", Path.of("/src/ABC-42-deploy"));
    }
}
