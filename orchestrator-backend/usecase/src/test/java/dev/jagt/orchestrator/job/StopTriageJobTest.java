package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.RoleConfig;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
import dev.jagt.orchestrator.service.StopPolicy;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.triage.StopTriageFailed;
import dev.jagt.orchestrator.service.triage.StopTriagePanel;
import dev.jagt.orchestrator.task.Master;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.StopTriage;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class StopTriageJobTest {

  private static final Arbiter.Profile ACT = new Arbiter.Profile(MasterMode.ACT, Set.of());

  @TempDir Path root;
  private final ConfigService config = mock(ConfigService.class);
  private final StopTriagePanel panel = mock(StopTriagePanel.class);
  private final StopTriage jagtRead =
      new StopTriage(
          StopTriage.Who.JAGT,
          "ships counted past a merge",
          "H",
          StopTriage.Lift.SAFE,
          "request merged",
          "");

  private OrchestratorPaths paths;
  private StateService state;

  @BeforeEach
  void taskStoppedOnShipsWhereTriageIsOn() {
    paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    state = stateService().withRoot(root).build();
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.SHIPS, 5, "4 ships"))
            .master(Master.defaults())
            .build());
    triage(true);
    when(panel.read(anyString(), any(), any(), anyString())).thenReturn(jagtRead);
  }

  private StopTriageJob job(
      OrchestratorPaths paths, StateService state, Arbiter.Profile masterProfile) {
    return new StopTriageJob(
        config,
        ignored -> masterProfile,
        panel,
        new TaskStops(
            new StopPolicy(
                ignored -> masterProfile,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(new StopLog(new JsonMapper(), paths))),
            state,
            Clock.systemUTC(),
            new FlowRules(new TaskChart(Composition.builtIn()))));
  }

  private void triage(boolean enabled) {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withRoles(Map.of("triage", new RoleConfig(enabled, null))));
  }

  @Test
  void stopIsReadOnceAndItsHoverGainsTheTriageLine() {
    StopTriageJob job = job(paths, state, Arbiter.Profile.OFF);

    job.runOn(state.tasks());
    job.runOn(state.tasks());

    verify(panel, times(1)).read(anyString(), any(), any(), anyString());
    assertThat(
            Objects.requireNonNull(
                    Objects.requireNonNull(state.task("ABC-42").orElseThrow().stop()).triage())
                .line())
        .isEqualTo("triage reads: jagt — ships counted past a merge (H), lift looks safe");
  }

  @Test
  void nothingIsReadWhereTriageIsNotEnabled() {
    triage(false);

    job(paths, state, Arbiter.Profile.OFF).runOn(state.tasks());

    verifyNoInteractions(panel);
    assertThat(Objects.requireNonNull(state.task("ABC-42").orElseThrow().stop()).triage()).isNull();
  }

  @Test
  void liftEndsTheReadAndTheNextStopIsReadAfresh() {
    StopTriageJob job = job(paths, state, Arbiter.Profile.OFF);
    job.runOn(state.tasks());

    state.updateTask("ABC-42", task -> task.lifted(6));
    assertThat(state.task("ABC-42").orElseThrow().stop()).isNull();
    state.updateTask(
        "ABC-42", task -> task.withStop(new TaskStop(TaskStop.Trigger.ROUNDS, 9, "4 rounds")));
    job.runOn(state.tasks());

    verify(panel, times(2)).read(anyString(), any(), any(), anyString());
    assertThat(Objects.requireNonNull(state.task("ABC-42").orElseThrow().stop()).triage())
        .isEqualTo(jagtRead);
  }

  @Test
  void pausedTaskIsNotRead() {
    state.updateTask(
        "ABC-42",
        task -> task.withStop(new TaskStop(TaskStop.Trigger.PAUSE, 5, "you paused it")));

    job(paths, state, Arbiter.Profile.OFF).runOn(state.tasks());

    verifyNoInteractions(panel);
  }

  @Test
  void stopTheMasterHoldsIsNotReadUntilTheMasterLeavesIt() {
    TaskStop stop = new TaskStop(TaskStop.Trigger.ROUNDS, 5, "4 rounds");
    state.updateTask("ABC-42", task -> task.withStop(stop));
    StopTriageJob job = job(paths, state, ACT);

    job.runOn(state.tasks());
    verify(panel, never()).read(anyString(), any(), any(), anyString());

    state.updateTask(
        "ABC-42", task -> task.withMaster(Master.defaults().withAskedStop(stop.key())));
    job.runOn(state.tasks());

    verify(panel, times(1)).read(anyString(), any(), any(), anyString());
  }

  @Test
  void failedReadSaysSoOnTheHoverAndTheStopIsNotAskedAgain() {
    when(panel.read(anyString(), any(), any(), anyString()))
        .thenThrow(new StopTriageFailed("git state changed"));
    StopTriageJob job = job(paths, state, Arbiter.Profile.OFF);

    job.runOn(state.tasks());
    job.runOn(state.tasks());

    verify(panel, times(1)).read(anyString(), any(), any(), anyString());
    assertThat(
            Objects.requireNonNull(
                    Objects.requireNonNull(state.task("ABC-42").orElseThrow().stop()).triage())
                .line())
        .isEqualTo("triage could not read: git state changed");
  }

  @Test
  void liftThatLooksSafeStillLeavesTheStopStanding() {

    job(paths, state, Arbiter.Profile.OFF).runOn(state.tasks());

    assertThat(state.task("ABC-42").orElseThrow().stopped()).isTrue();
  }
}
