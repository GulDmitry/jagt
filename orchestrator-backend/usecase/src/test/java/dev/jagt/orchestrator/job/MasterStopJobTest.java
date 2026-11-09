package dev.jagt.orchestrator.job;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.Rounds;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.master.MasterStopAnswer;
import dev.jagt.orchestrator.service.master.MasterStopPanel;
import dev.jagt.orchestrator.task.Master;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class MasterStopJobTest {

  private final ConfigService config = mock(ConfigService.class);
  private final MasterStopPanel panel = mock(MasterStopPanel.class);
  private final MasterStopMoves moves = mock(MasterStopMoves.class);

  private StateService stateWith(Path root, TaskStop stop, Master master) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).stop(stop).master(master).build());
    return state;
  }

  private void mode(String mode) {
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig(mode, null, null, null, null)));
  }

  @Test
  void ABC42StoppedOnROUNDSInActTheMasterLiftsIt(@TempDir Path root) {
    StateService state =
        stateWith(
            root,
            new TaskStop(TaskStop.Trigger.ROUNDS, 5, "verify sent 3 rounds"),
            Master.defaults());
    mode("act");
    when(panel.ask(anyString(), any(), any()))
        .thenReturn(
            Optional.of(
                new MasterStopAnswer(
                    MasterStopAnswer.Kind.LIFT, "each round answered a new report")));

    new MasterStopJob(
            config,
            panel,
            moves,
            new TaskTurns(state, Clock.systemUTC()),
            new Rounds(config, ignored -> false, () -> false))
        .runOn(state.tasks());

    verify(moves).lift("ABC-42");
  }

  @Test
  void aStopTheHumanSetIsNeverAskedOfTheMaster(@TempDir Path root) {
    StateService state =
        stateWith(
            root,
            new TaskStop(TaskStop.Trigger.PAUSE, 5, "until the managers answer"),
            Master.defaults());
    mode("act");

    new MasterStopJob(
            config,
            panel,
            moves,
            new TaskTurns(state, Clock.systemUTC()),
            new Rounds(config, ignored -> false, () -> false))
        .runOn(state.tasks());

    verify(panel, never()).ask(anyString(), any(), any());
  }

  @Test
  void theStopStaysTheHumansWhereTheMasterAnswersHumanAndIsNotAskedAgain(@TempDir Path root) {
    StateService state =
        stateWith(root, new TaskStop(TaskStop.Trigger.SHIPS, 5, "4 ships"), Master.defaults());
    mode("act");
    when(panel.ask(anyString(), any(), any()))
        .thenReturn(
            Optional.of(
                new MasterStopAnswer(
                    MasterStopAnswer.Kind.HUMAN, "the tree is the human's to judge")));
    MasterStopJob job =
        new MasterStopJob(
            config,
            panel,
            moves,
            new TaskTurns(state, Clock.systemUTC()),
            new Rounds(config, ignored -> false, () -> false));

    job.runOn(state.tasks());
    job.runOn(state.tasks());

    verify(panel, times(1)).ask(anyString(), any(), any());
    verify(moves, never()).lift(anyString());
  }

  @Test
  void aSecondStopIsTheHumansOnceTheMasterLiftedOneSinceTheHumansLastLift(@TempDir Path root) {
    StateService state =
        stateWith(
            root,
            new TaskStop(TaskStop.Trigger.STALLED, 9, "3 idle rounds"),
            Master.defaults().withLiftedStops(1));
    mode("act");

    new MasterStopJob(
            config,
            panel,
            moves,
            new TaskTurns(state, Clock.systemUTC()),
            new Rounds(config, ignored -> false, () -> false))
        .runOn(state.tasks());

    verify(panel, never()).ask(anyString(), any(), any());
  }

  @Test
  void theSessionIsToldTheInstructionWhereTheMasterReturnsTheTask(@TempDir Path root) {
    StateService state =
        stateWith(
            root,
            new TaskStop(TaskStop.Trigger.UNCHANGED_FAILURE, 5, "unit-tests again"),
            Master.defaults());
    mode("act");
    when(panel.ask(anyString(), any(), any()))
        .thenReturn(
            Optional.of(
                new MasterStopAnswer(
                    MasterStopAnswer.Kind.RETURN, "fix the fixture, not the test")));
    when(moves.lift("ABC-42")).thenReturn(true);

    new MasterStopJob(
            config,
            panel,
            moves,
            new TaskTurns(state, Clock.systemUTC()),
            new Rounds(config, ignored -> false, () -> false))
        .runOn(state.tasks());

    verify(moves).returned("ABC-42", "fix the fixture, not the test");
  }

  @Test
  void nothingIsAskedOfTheMasterInJudge(@TempDir Path root) {
    StateService state =
        stateWith(
            root,
            new TaskStop(TaskStop.Trigger.ROUNDS, 5, "verify sent 3 rounds"),
            Master.defaults());
    mode("judge");

    new MasterStopJob(
            config,
            panel,
            moves,
            new TaskTurns(state, Clock.systemUTC()),
            new Rounds(config, ignored -> false, () -> false))
        .runOn(state.tasks());

    verify(panel, never()).ask(anyString(), any(), any());
  }

  @Test
  void aTaskTheMasterLeftToTheHumanIsNeverAskedAboutItsStop(@TempDir Path root) {
    StateService state =
        stateWith(
            root,
            new TaskStop(TaskStop.Trigger.ROUNDS, 5, "verify sent 3 rounds"),
            Master.defaults().withLeft("deploy"));
    mode("act");

    new MasterStopJob(
            config,
            panel,
            moves,
            new TaskTurns(state, Clock.systemUTC()),
            new Rounds(config, ignored -> false, () -> false))
        .runOn(state.tasks());

    verify(panel, never()).ask(anyString(), any(), any());
  }

  @Test
  void theStopIsNotAskedAgainWhereTheAskFailed(@TempDir Path root) {
    StateService state =
        stateWith(
            root,
            new TaskStop(TaskStop.Trigger.ROUNDS, 5, "verify sent 3 rounds"),
            Master.defaults());
    mode("act");
    when(panel.ask(anyString(), any(), any()))
        .thenThrow(new IllegalStateException("model unreachable"));
    MasterStopJob job =
        new MasterStopJob(
            config,
            panel,
            moves,
            new TaskTurns(state, Clock.systemUTC()),
            new Rounds(config, ignored -> false, () -> false));

    assertThatThrownBy(() -> job.runOn(state.tasks())).isInstanceOf(IllegalStateException.class);
    job.runOn(state.tasks());

    assertThat(state.task("ABC-42").orElseThrow().masterOrNone().askedStop()).isNotEmpty();
    verify(panel, times(1)).ask(anyString(), any(), any());
  }

  @Test
  void theStopStaysTheHumansWhereTheMasterReturnsATaskWaitingOnAPlan(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())));
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING)
            .stop(new TaskStop(TaskStop.Trigger.UNCHANGED_FAILURE, 5, "unit-tests again"))
            .master(Master.defaults())
            .build());
    mode("act");
    when(panel.ask(anyString(), any(), any()))
        .thenReturn(
            Optional.of(
                new MasterStopAnswer(
                    MasterStopAnswer.Kind.RETURN, "fix the fixture, not the test")));

    new MasterStopJob(
            config,
            panel,
            moves,
            new TaskTurns(state, Clock.systemUTC()),
            new Rounds(config, ignored -> false, () -> false))
        .runOn(state.tasks());

    verify(moves, never()).lift(anyString());
  }

  @Test
  void theSessionStartsAgainOneTierUpWhereTheMasterAnswersEscalate(@TempDir Path root) {
    StateService state =
        stateWith(
            root, new TaskStop(TaskStop.Trigger.ROUNDS, 5, "3 rounds"), Master.defaults());
    mode("act");
    when(panel.ask(anyString(), any(), any()))
        .thenReturn(
            Optional.of(
                new MasterStopAnswer(MasterStopAnswer.Kind.ESCALATE, "sonnet looped on a fixture")));

    new MasterStopJob(
            config,
            panel,
            moves,
            new TaskTurns(state, Clock.systemUTC()),
            new Rounds(config, ignored -> false, () -> false))
        .runOn(state.tasks());

    verify(moves).escalate("ABC-42");
    verify(moves, never()).lift(anyString());
  }

  @Test
  void theStopStaysTheHumansWhereTheMasterAnswersEscalateAndMineKeepsEscalate(
      @TempDir Path root) {
    StateService state =
        stateWith(
            root, new TaskStop(TaskStop.Trigger.ROUNDS, 5, "3 rounds"), Master.defaults());
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig(
                        "act", null, null, List.of("escalate"), null)));
    when(panel.ask(anyString(), any(), any()))
        .thenReturn(
            Optional.of(
                new MasterStopAnswer(MasterStopAnswer.Kind.ESCALATE, "sonnet looped on a fixture")));

    new MasterStopJob(
            config,
            panel,
            moves,
            new TaskTurns(state, Clock.systemUTC()),
            new Rounds(config, ignored -> false, () -> false))
        .runOn(state.tasks());

    verify(moves, never()).escalate(anyString());
  }
}
