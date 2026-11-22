package dev.jagt.orchestrator.job.deploy;

import static dev.jagt.orchestrator.job.TestAskedDeployBuilder.askedDeploy;
import static dev.jagt.orchestrator.job.TestFlowReportsBuilder.flowReports;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.stop.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.session.Standing;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.turn.Step;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeployCheckJobAcceptanceTest {

  @Test
  void asksTheSessionNothingWhereAcceptanceChecksTheDeploy(@TempDir Path root) {
    ConfigService config = mock(ConfigService.class);
    final StepRelay relay = mock(StepRelay.class);
    final DeployPipelines pipelines = mock(DeployPipelines.class);
    StateService state = stateService().withRoot(root).build();
    state.putTask("ABC-42", askedDeploy().withAskedDigest(null).build());
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of("proj", new ProjectConfig("/repo", "origin/main", "dev", List.of())))
                .withRoles(
                    Map.of("acceptance", new ConfigService.ConfigFile.RoleConfig(true, null))));
    when(config.anyCheckerEnabled()).thenReturn(true);
    when(pipelines.read(eq("ABC-42"), any()))
        .thenReturn(new DeployPipelines.Reading(DeployPipelines.Kind.GREEN, "", "", "", List.of()));

    new DeployCheckJob(
            config,
            relay,
            pipelines,
            flowReports().build(state, new FakeClock()),
            taskStops().withRoot(root).build(state, new FakeClock()))
        .runOn(state.tasks());

    verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
    assertThat(
            Objects.requireNonNull(state.task("ABC-42").orElseThrow().work().deployment())
                .askedDigest())
        .isNotNull();
  }
}
