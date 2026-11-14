package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestStopTriageJobBuilder.stopTriageJob;
import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.stubbing.Answer;

class StopTriageJobTest {

  private static final Arbiter.Profile ACT = new Arbiter.Profile(MasterMode.ACT, Set.of());

  @TempDir Path root;
  private final ConfigService config = mock(ConfigService.class);
  private final StopTriagePanel panel = mock(StopTriagePanel.class);
  private static final StopTriage jagtRead =
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
    paths = orchestratorPaths().withRoot(root).build();
    state = stateService().withRoot(root).build();
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.SHIPS, 5, "4 ships"))
            .master(Master.defaults())
            .build());
    when(config.load()).thenReturn(stopTriageJob().config());
    when(panel.read(anyString(), any(), any(), anyString())).thenReturn(jagtRead);
  }

  static Stream<Arguments> triageReads() {
    return Stream.of(
        arguments(
            "read gains the triage line",
            (Answer<StopTriage>) call -> jagtRead,
            "triage reads: jagt — ships counted past a merge (H), lift looks safe"),
        arguments(
            "failed read says so and the stop is not asked again",
            (Answer<StopTriage>)
                call -> {
                  throw new StopTriageFailed("git state changed");
                },
            "triage could not read: git state changed"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("triageReads")
  void stopIsReadOnceAndItsHoverSaysWhatTheReadWas(
      String situation, Answer<StopTriage> read, String line) {
    when(panel.read(anyString(), any(), any(), anyString())).thenAnswer(read);
    StopTriageJob job = stopTriageJob().build(config, panel, state, paths, Clock.systemUTC());

    job.runOn(state.tasks());
    job.runOn(state.tasks());

    verify(panel, times(1)).read(anyString(), any(), any(), anyString());
    assertThat(
            Objects.requireNonNull(
                    Objects.requireNonNull(state.task("ABC-42").orElseThrow().stop()).triage())
                .line())
        .isEqualTo(line);
  }

  @Test
  void nothingIsReadWhereTriageIsNotEnabled() {
    when(config.load()).thenReturn(stopTriageJob().withTriage(false).config());

    stopTriageJob().build(config, panel, state, paths, Clock.systemUTC()).runOn(state.tasks());

    verifyNoInteractions(panel);
    assertThat(Objects.requireNonNull(state.task("ABC-42").orElseThrow().stop()).triage()).isNull();
  }

  @Test
  void liftEndsTheReadAndTheNextStopIsReadAfresh() {
    StopTriageJob job = stopTriageJob().build(config, panel, state, paths, Clock.systemUTC());
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
        "ABC-42", task -> task.withStop(new TaskStop(TaskStop.Trigger.PAUSE, 5, "you paused it")));

    stopTriageJob().build(config, panel, state, paths, Clock.systemUTC()).runOn(state.tasks());

    verifyNoInteractions(panel);
  }

  @Test
  void stopTheMasterHoldsIsNotReadUntilTheMasterLeavesIt() {
    TaskStop stop = new TaskStop(TaskStop.Trigger.ROUNDS, 5, "4 rounds");
    state.updateTask("ABC-42", task -> task.withStop(stop));
    StopTriageJob job =
        stopTriageJob()
            .withMasterProfile(ACT)
            .build(config, panel, state, paths, Clock.systemUTC());

    job.runOn(state.tasks());
    verify(panel, never()).read(anyString(), any(), any(), anyString());

    state.updateTask(
        "ABC-42", task -> task.withMaster(Master.defaults().withAskedStop(stop.key())));
    job.runOn(state.tasks());

    verify(panel, times(1)).read(anyString(), any(), any(), anyString());
  }

  @Test
  void liftThatLooksSafeStillLeavesTheStopStanding() {
    stopTriageJob().build(config, panel, state, paths, Clock.systemUTC()).runOn(state.tasks());

    assertThat(state.task("ABC-42").orElseThrow().stopped()).isTrue();
  }
}
