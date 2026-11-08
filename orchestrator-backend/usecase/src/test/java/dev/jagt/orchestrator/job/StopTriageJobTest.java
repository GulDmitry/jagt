package dev.jagt.orchestrator.job;

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
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.RoleConfig;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class StopTriageJobTest {

  private static final Arbiter.Profile ACT = new Arbiter.Profile(MasterMode.ACT, Set.of());

  private final ConfigService config = mock(ConfigService.class);
  private final StopTriagePanel panel = mock(StopTriagePanel.class);
  private final Notifications notifications = mock(Notifications.class);
  private final StopTriage jagtRead =
      new StopTriage(
          StopTriage.Who.JAGT,
          "ships counted past a merge",
          "H",
          StopTriage.Lift.SAFE,
          "request merged",
          "");

  private static OrchestratorPaths pathsIn(Path root) {
    return new OrchestratorPaths(
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString()));
  }

  private StateService stateWith(OrchestratorPaths paths, TaskStop stop, Master master) {
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).stop(stop).master(master).build());
    return state;
  }

  private StopTriageJob job(
      OrchestratorPaths paths, StateService state, Arbiter.Profile masterProfile) {
    return new StopTriageJob(
        config,
        ignored -> masterProfile,
        panel,
        new TaskStops(
            ignored -> masterProfile,
            state,
            ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
            new StopAnnouncements(notifications, new StopLog(new JsonMapper(), paths)),
            Clock.systemUTC()));
  }

  private void triage(boolean enabled) {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withRoles(Map.of("triage", new RoleConfig(enabled, null, null, null))));
  }

  @Test
  void aStopIsReadOnceAndItsHoverGainsTheTriageLine(@TempDir Path root) {
    OrchestratorPaths paths = pathsIn(root);
    StateService state =
        stateWith(
            paths, new TaskStop(TaskStop.Trigger.SHIPS, 5, "4 ships"), Master.defaults());
    triage(true);
    when(panel.read(anyString(), any(), any(), anyString())).thenReturn(jagtRead);
    StopTriageJob job = job(paths, state, Arbiter.Profile.OFF);

    job.runOn(state.tasks());
    job.runOn(state.tasks());

    verify(panel, times(1)).read(anyString(), any(), any(), anyString());
    assertThat(state.task("ABC-42").orElseThrow().stop().triage().line())
        .isEqualTo("triage reads: jagt — ships counted past a merge (H), lift looks safe");
  }

  @Test
  void nothingIsReadWhereTriageIsNotEnabled(@TempDir Path root) {
    OrchestratorPaths paths = pathsIn(root);
    StateService state =
        stateWith(
            paths, new TaskStop(TaskStop.Trigger.SHIPS, 5, "4 ships"), Master.defaults());
    triage(false);

    job(paths, state, Arbiter.Profile.OFF).runOn(state.tasks());

    verifyNoInteractions(panel);
    assertThat(state.task("ABC-42").orElseThrow().stop().triage()).isNull();
  }

  @Test
  void aLiftEndsTheReadAndTheNextStopIsReadAfresh(@TempDir Path root) {
    OrchestratorPaths paths = pathsIn(root);
    StateService state =
        stateWith(
            paths, new TaskStop(TaskStop.Trigger.SHIPS, 5, "4 ships"), Master.defaults());
    triage(true);
    when(panel.read(anyString(), any(), any(), anyString())).thenReturn(jagtRead);
    StopTriageJob job = job(paths, state, Arbiter.Profile.OFF);
    job.runOn(state.tasks());

    state.updateTask("ABC-42", task -> task.lifted(6));
    assertThat(state.task("ABC-42").orElseThrow().stop()).isNull();
    state.updateTask(
        "ABC-42", task -> task.withStop(new TaskStop(TaskStop.Trigger.ROUNDS, 9, "4 rounds")));
    job.runOn(state.tasks());

    verify(panel, times(2)).read(anyString(), any(), any(), anyString());
    assertThat(state.task("ABC-42").orElseThrow().stop().triage()).isEqualTo(jagtRead);
  }

  @Test
  void aPausedTaskIsNotRead(@TempDir Path root) {
    OrchestratorPaths paths = pathsIn(root);
    StateService state =
        stateWith(
            paths, new TaskStop(TaskStop.Trigger.PAUSE, 5, "you paused it"), Master.defaults());
    triage(true);

    job(paths, state, Arbiter.Profile.OFF).runOn(state.tasks());

    verifyNoInteractions(panel);
  }

  @Test
  void aStopTheMasterHoldsIsNotReadUntilTheMasterLeavesIt(@TempDir Path root) {
    OrchestratorPaths paths = pathsIn(root);
    TaskStop stop = new TaskStop(TaskStop.Trigger.ROUNDS, 5, "4 rounds");
    StateService state = stateWith(paths, stop, Master.defaults());
    triage(true);
    when(panel.read(anyString(), any(), any(), anyString())).thenReturn(jagtRead);
    StopTriageJob job = job(paths, state, ACT);

    job.runOn(state.tasks());
    verify(panel, never()).read(anyString(), any(), any(), anyString());

    state.updateTask(
        "ABC-42", task -> task.withMaster(Master.defaults().withAskedStop(stop.key())));
    job.runOn(state.tasks());

    verify(panel, times(1)).read(anyString(), any(), any(), anyString());
  }

  @Test
  void aFailedReadSaysSoOnTheHoverAndTheStopIsNotAskedAgain(@TempDir Path root) {
    OrchestratorPaths paths = pathsIn(root);
    StateService state =
        stateWith(
            paths, new TaskStop(TaskStop.Trigger.SHIPS, 5, "4 ships"), Master.defaults());
    triage(true);
    when(panel.read(anyString(), any(), any(), anyString()))
        .thenThrow(new StopTriageFailed("git state changed"));
    StopTriageJob job = job(paths, state, Arbiter.Profile.OFF);

    job.runOn(state.tasks());
    job.runOn(state.tasks());

    verify(panel, times(1)).read(anyString(), any(), any(), anyString());
    assertThat(state.task("ABC-42").orElseThrow().stop().triage().line())
        .isEqualTo("triage could not read: git state changed");
  }

  @Test
  void aLiftThatLooksSafeStillLeavesTheStopStandingAndTellsNobody(@TempDir Path root) {
    OrchestratorPaths paths = pathsIn(root);
    StateService state =
        stateWith(
            paths, new TaskStop(TaskStop.Trigger.SHIPS, 5, "4 ships"), Master.defaults());
    triage(true);
    when(panel.read(anyString(), any(), any(), anyString())).thenReturn(jagtRead);

    job(paths, state, Arbiter.Profile.OFF).runOn(state.tasks());

    assertThat(state.task("ABC-42").orElseThrow().stopped()).isTrue();
    verifyNoInteractions(notifications);
  }
}
