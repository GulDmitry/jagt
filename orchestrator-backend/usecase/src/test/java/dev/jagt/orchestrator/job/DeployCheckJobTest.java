package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.MissLog;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import java.util.List;
import java.util.Map;

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

class DeployCheckJobTest {

    private static final DeployPipelines.Reading GREEN = new DeployPipelines.Reading(DeployPipelines.Kind.GREEN, "", "", "");

    private StateService state(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    @Test
    void asksADeployedTasksSessionToCheckItWhereItLandedOnceItsPipelineIsGreen(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        StepRelay relay = mock(StepRelay.class);
        DeployPipelines pipelines = mock(DeployPipelines.class);
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployment(new Deployment(1, List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")), 1, null)));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        when(pipelines.read(eq("ABC-42"), any())).thenReturn(GREEN);
        when(relay.standing("ABC-42")).thenReturn(Standing.ABSENT);

        new DeployCheckJob(config, relay, pipelines, new FlowReports(state, Clock.systemUTC())).runOn(state.tasks());

        verify(relay).relay(eq("ABC-42"), eq(Step.DEPLOY), contains("- proj: 01234567 on dev"), any(Standing.class));
    }

    @Test
    void asksNothingAndSaysItWaitsOnItsPipelineWhileTheDeployPipelineIsRunning(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        StepRelay relay = mock(StepRelay.class);
        DeployPipelines pipelines = mock(DeployPipelines.class);
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployment(new Deployment(1, List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")), 1, null)));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        when(pipelines.read(eq("ABC-42"), any())).thenReturn(new DeployPipelines.Reading(
                DeployPipelines.Kind.WAITING, "proj 01234567: pipeline running", "", ""));

        new DeployCheckJob(config, relay, pipelines, new FlowReports(state, Clock.systemUTC())).runOn(state.tasks());

        verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
        assertThat(state.task("ABC-42").orElseThrow().message())
                .isEqualTo("deployed to dev — waiting on its pipeline: proj 01234567: pipeline running");
    }

    @Test
    void handsADeployBackToItsSessionWhenItsPipelineIsRedOnTheCodeAndAsksNothing(@TempDir Path root) {
        StateService state = state(root);
        ConfigService config = mock(ConfigService.class);
        StepRelay relay = mock(StepRelay.class);
        DeployPipelines pipelines = mock(DeployPipelines.class);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployment(new Deployment(1, List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")), 1, null)));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        when(pipelines.read(eq("ABC-42"), any())).thenReturn(new DeployPipelines.Reading(DeployPipelines.Kind.RED,
                "red pipeline on the deploy commit: proj 01234567", "proj", "https://git.example.invalid/p/7"));

        new DeployCheckJob(config, relay, pipelines, new FlowReports(state, Clock.systemUTC())).runOn(state.tasks());

        TaskState handed = state.task("ABC-42").orElseThrow();
        assertThat(handed.status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
        assertThat(handed.message())
                .isEqualTo("red pipeline on the deploy commit: proj 01234567 https://git.example.invalid/p/7");
        verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
    }

    @Test
    void asksOncePerDeployThoughAnotherRelayFollowedAndJagtRestarted(@TempDir Path root) throws Exception {
        ConfigService config = mock(ConfigService.class);
        DeployPipelines pipelines = mock(DeployPipelines.class);
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        Files.copy(Path.of("../../session-cases.md.dist"), root.resolve("session-cases.md.dist"));
        Path worktree = Files.createDirectories(root.resolve("ABC-42"));
        new StateService(new JsonMapper(), paths).putTask("ABC-42", TaskState.builder("proj", worktree.toString(),
                TaskStatus.DEPLOYED).build().withDeployment(new Deployment(1, List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")), 1, null)));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        when(pipelines.read(eq("ABC-42"), any())).thenReturn(GREEN);
        AgentSessions sessions = mock(AgentSessions.class);
        StateService before = new StateService(new JsonMapper(), paths);
        StepRelay relay = new StepRelay(new SessionBrief(paths), sessions, new CaseDoor(new SessionCases(paths), new MasterCases(paths), before,
                new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                new TaskStops(before, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), before, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(before, Clock.systemUTC()));
        new DeployCheckJob(config, relay, pipelines, new FlowReports(before, Clock.systemUTC())).runOn(before.tasks());
        relay.relay("ABC-42", Step.ANSWERED, "The Master answered your question, standing in for the human:\nyes", relay.standing("ABC-42"));
        StateService restarted = new StateService(new JsonMapper(), paths);

        new DeployCheckJob(config, new StepRelay(new SessionBrief(paths), sessions,
                new CaseDoor(new SessionCases(paths), new MasterCases(paths), restarted, new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                        new TaskStops(restarted, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault, new StopAnnouncements(new Notifications(List.of(), restarted, Runnable::run), new StopLog(new JsonMapper(), paths)), Clock.systemUTC())),
                new TaskTurns(restarted, Clock.systemUTC())), pipelines, new FlowReports(restarted, Clock.systemUTC())).runOn(restarted.tasks());

        verify(sessions, times(1)).writeTaskContext(eq("ABC-42"), contains("ABC-42 is deployed"));
    }

    @Test
    void asksNothingWhileTheFixOfAFailedCheckIsUnderway(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        StepRelay relay = mock(StepRelay.class);
        DeployPipelines pipelines = mock(DeployPipelines.class);
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()
                .withDeployment(new Deployment(1, List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")), 1, null)));

        new DeployCheckJob(config, relay, pipelines, new FlowReports(state, Clock.systemUTC())).runOn(state.tasks());

        verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
        verify(pipelines, never()).read(anyString(), any());
    }

    @Test
    void namesAnEightCharacterCommitWhole(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        StepRelay relay = mock(StepRelay.class);
        DeployPipelines pipelines = mock(DeployPipelines.class);
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployment(new Deployment(1, List.of(Deployment.Target.of("proj", "dev").withMerge("01234567")), 1, null)));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));
        when(pipelines.read(eq("ABC-42"), any())).thenReturn(GREEN);
        when(relay.standing("ABC-42")).thenReturn(Standing.ABSENT);

        new DeployCheckJob(config, relay, pipelines, new FlowReports(state, Clock.systemUTC())).runOn(state.tasks());

        verify(relay).relay(eq("ABC-42"), eq(Step.DEPLOY), contains("- proj: 01234567 on dev"), any(Standing.class));
    }

    @Test
    void asksNothingOfADeployedTaskWhoseDeployLeftNoCommit(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        StepRelay relay = mock(StepRelay.class);
        DeployPipelines pipelines = mock(DeployPipelines.class);
        StateService state = state(root);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));

        new DeployCheckJob(config, relay, pipelines, new FlowReports(state, Clock.systemUTC())).runOn(state.tasks());

        verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
    }

    @Test
    void readsNoPipelineAgainForADeployWhoseCheckWasAlreadyAsked(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        StepRelay relay = mock(StepRelay.class);
        DeployPipelines pipelines = mock(DeployPipelines.class);
        StateService state = state(root);
        String asked = "ABC-42 is deployed:\n- proj: 01234567 on dev";
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployment(new Deployment(1, List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                        1, Turn.digestOf(asked))));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));

        new DeployCheckJob(config, relay, pipelines, new FlowReports(state, Clock.systemUTC())).runOn(state.tasks());

        verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
        verify(pipelines, never()).read(anyString(), any());
    }
}
