package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.MissLog;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeployCheckJobTest {

    @Test
    void asksADeployedTasksSessionToCheckItWhereItLanded() {
        StateService state = mock(StateService.class);
        ConfigService config = mock(ConfigService.class);
        StepRelay relay = mock(StepRelay.class);
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployCommit("proj", "0123456789abcdef")));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));

        new DeployCheckJob(state, config, relay).run();

        verify(relay).relay(eq("ABC-42"), eq("deploy"), contains("- proj: 01234567 on dev"));
    }

    @Test
    void asksOncePerDeployThoughAnotherRelayFollowedAndJagtRestarted(@TempDir Path root) throws Exception {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Path worktree = Files.createDirectories(root.resolve("ABC-42"));
        new StateService(new JsonMapper(), paths).putTask("ABC-42", TaskState.builder("proj", worktree.toString(),
                TaskStatus.DEPLOYED).build().withDeployCommit("proj", "0123456789abcdef"));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        AgentSessions sessions = mock(AgentSessions.class);
        StateService before = new StateService(new JsonMapper(), paths);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, new CaseDoor(new SessionCases(paths), new MasterCases(paths), before,
                new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                new TaskStops(before, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(before, Clock.systemUTC()));
        new DeployCheckJob(before, config, relay).run();
        relay.relay("ABC-42", "answered", "The Master answered your question, standing in for the human:\nyes");
        StateService restarted = new StateService(new JsonMapper(), paths);

        new DeployCheckJob(restarted, config, new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), restarted, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(restarted, new Notifications(List.of(), Runnable::run), Clock.systemUTC())),
                new TaskTurns(restarted, Clock.systemUTC()))).run();

        verify(sessions, times(1)).writeTaskContext(eq("ABC-42"), contains("ABC-42 is deployed"));
    }

    @Test
    void asksNothingWhileTheFixOfAFailedCheckIsUnderway() {
        StateService state = mock(StateService.class);
        ConfigService config = mock(ConfigService.class);
        StepRelay relay = mock(StepRelay.class);
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()
                .withDeployCommit("proj", "0123456789abcdef")));

        new DeployCheckJob(state, config, relay).run();

        verify(relay, never()).relay(anyString(), anyString(), anyString());
    }

    @Test
    void namesAnEightCharacterCommitWhole() {
        StateService state = mock(StateService.class);
        ConfigService config = mock(ConfigService.class);
        StepRelay relay = mock(StepRelay.class);
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployCommit("proj", "01234567")));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));

        new DeployCheckJob(state, config, relay).run();

        verify(relay).relay(eq("ABC-42"), eq("deploy"), contains("- proj: 01234567 on dev"));
    }

    @Test
    void asksNothingOfADeployedTaskWhoseDeployLeftNoCommit() {
        StateService state = mock(StateService.class);
        ConfigService config = mock(ConfigService.class);
        StepRelay relay = mock(StepRelay.class);
        when(state.tasks()).thenReturn(Map.of("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));

        new DeployCheckJob(state, config, relay).run();

        verify(relay, never()).relay(anyString(), anyString(), anyString());
    }
}
