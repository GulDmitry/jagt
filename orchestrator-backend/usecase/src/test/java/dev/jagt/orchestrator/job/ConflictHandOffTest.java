package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.MissLog;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import tools.jackson.databind.json.JsonMapper;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.Conflict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        Files.createDirectories(root.resolve("roles/session"));
        Files.copy(Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
        state.putTask("ABC-42", TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT).build());

        new ConflictHandOff(sessions, new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(ignored -> Arbiter.Profile.OFF, state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC())), stall, finish).handle("ABC-42", new DeployConflicts.WaitingConflict(
                Path.of("/src/ABC-42-deploy"), false, new Conflict.Progress(7, 0, "d"), null, false, new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0)));

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
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(ignored -> Arbiter.Profile.OFF, state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC())), stall, finish).handle("ABC-42", new DeployConflicts.WaitingConflict(
                Path.of("/src/ABC-42-deploy"), false, new Conflict.Progress(7, 0, "d"), null, false, Standing.ABSENT));

        verify(sessions, never()).writeTaskContext(eq("ABC-42"), anyString());
    }

    @Test
    void watchesAHandedConflictForProgress(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        DeployConflicts.WaitingConflict conflict = new DeployConflicts.WaitingConflict(Path.of("/src/ABC-42-deploy"),
                false, new Conflict.Progress(7, 0, "d"), null, false, Standing.ABSENT);
        when(stall.handed("ABC-42")).thenReturn(true);

        new ConflictHandOff(sessions, new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(ignored -> Arbiter.Profile.OFF, state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC())), stall, finish).handle("ABC-42", conflict);

        verify(stall).watch("ABC-42", conflict);
    }

    @Test
    void finishesNothingWhilePathsRemainUnstaged(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        when(stall.handed("ABC-42")).thenReturn(true);

        new ConflictHandOff(sessions, new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(ignored -> Arbiter.Profile.OFF, state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC())), stall, finish).handle("ABC-42", new DeployConflicts.WaitingConflict(
                Path.of("/src/ABC-42-deploy"), false, new Conflict.Progress(7, 0, "d"), null, false, Standing.ABSENT));

        verifyNoInteractions(finish);
    }

    @Test
    void finishesTheDeployOnceTheResolutionIsStagedInFull(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        new ConflictHandOff(sessions, new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(ignored -> Arbiter.Profile.OFF, state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC())), stall, finish).handle("ABC-42", new DeployConflicts.WaitingConflict(
                Path.of("/src/ABC-42-deploy"), true, new Conflict.Progress(0, 7, "d"), null, false, Standing.ABSENT));

        verify(finish).press("ABC-42", Path.of("/src/ABC-42-deploy"));
    }

    @Test
    void handsADeployWhosePipelineWentRedAsAFixOfTheMergeResultInItsWorktree(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        Files.createDirectories(root.resolve("roles/session"));
        Files.copy(Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
        state.putTask("ABC-42", TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT).build());

        new ConflictHandOff(sessions, new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(ignored -> Arbiter.Profile.OFF, state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC())), stall, finish).handle("ABC-42", new DeployConflicts.WaitingConflict(
                Path.of("/src/ABC-42-deploy"), false, new Conflict.Progress(0, 0, "d"),
                "red pipeline on the deploy commit: proj 01234567 https://git.example.invalid/p/7", false, new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0)));

        verify(sessions).admit("ABC-42", Path.of("/src/ABC-42-deploy"));
        verify(sessions).writeTaskContext(eq("ABC-42"), contains("ABC-42 deployed, but its red pipeline on the deploy commit: proj 01234567"));
        verify(sessions).writeTaskContext(eq("ABC-42"), contains("Fix the merge result in /src/ABC-42-deploy"));
    }

    @Test
    void handsARevertConflictToTheSessionAsTheRevertWorktreeToResolve(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        Files.createDirectories(root.resolve("roles/session"));
        Files.copy(Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
        state.putTask("ABC-42", TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT).build());

        new ConflictHandOff(sessions, new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(ignored -> Arbiter.Profile.OFF, state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC())), stall, finish).handle("ABC-42", new DeployConflicts.WaitingConflict(
                Path.of("/src/ABC-42-revert"), false, new Conflict.Progress(2, 0, "r"), null, true, new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0)));

        verify(sessions).admit("ABC-42", Path.of("/src/ABC-42-revert"));
        verify(sessions).writeTaskContext(eq("ABC-42"), contains("ABC-42 did not revert"));
    }

    @Test
    void handsNothingAndStopsNothingWhenTheConflictEndedSinceItWasRead(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        Files.createDirectories(root.resolve("roles/session"));
        Files.copy(Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
        state.putTask("ABC-42", TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOYED).build());

        new ConflictHandOff(sessions, new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), state, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(ignored -> Arbiter.Profile.OFF, state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(state, Clock.systemUTC())), stall, finish).handle("ABC-42", new DeployConflicts.WaitingConflict(
                Path.of("/src/ABC-42-deploy"), false, new Conflict.Progress(0, 0, "d"),
                "red pipeline on the deploy commit: proj 01234567 https://git.example.invalid/p/7", false,
                new Standing(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0)));

        assertThat(state.task("ABC-42").orElseThrow().stop()).isNull();
        verify(sessions, never()).writeTaskContext(eq("ABC-42"), anyString());
    }
}
