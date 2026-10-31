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
import dev.jagt.orchestrator.task.TaskStop;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeployCheckJobQaTest {

    @Test
    void asksTheSessionNothingWhereQaChecksTheDeploy(@TempDir Path root) {
        ConfigService config = mock(ConfigService.class);
        StepRelay relay = mock(StepRelay.class);
        DeployPipelines pipelines = mock(DeployPipelines.class);
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()
                .withDeployment(new Deployment(1, List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")), 1, null)));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))).withQa(
                new ConfigService.ConfigFile.QaConfig(true, null, List.of())));
        when(pipelines.read(eq("ABC-42"), any())).thenReturn(new DeployPipelines.Reading(
                DeployPipelines.Kind.GREEN, "", "", "", List.of()));

        new DeployCheckJob(new CheckerGate(config), relay, pipelines, new FlowReports(state, Clock.systemUTC()),
                new TaskStops(state, ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(new Notifications(List.of(), state, Runnable::run), new StopLog(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())))),
                Clock.systemUTC())).runOn(state.tasks());

        verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
        assertThat(state.task("ABC-42").orElseThrow().deployment().askedDigest()).isNotNull();
    }
}
