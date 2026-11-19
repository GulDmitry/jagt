package dev.jagt.orchestrator.job.master;

import static dev.jagt.orchestrator.job.master.TestMasterStopJobBuilder.masterStopJob;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterStopAnswer;
import dev.jagt.orchestrator.service.master.MasterStopPanel;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.verification.VerificationMode;

class MasterStopJobTest {

  @TempDir Path root;

  private final ConfigService config = mock(ConfigService.class);
  private final MasterStopPanel panel = mock(MasterStopPanel.class);
  private final MasterStopMoves moves = mock(MasterStopMoves.class);
  private StateService state;

  @BeforeEach
  void openState() {
    state = stateService().withRoot(root).build();
  }

  static Stream<Arguments> neverAskedCases() {
    return Stream.of(
        arguments(
            "stop the human set",
            masterStopJob()
                .withStop(new TaskStop(TaskStop.Trigger.PAUSE, 5, "until the managers answer"))),
        arguments(
            "second stop once the master lifted one since the human's last lift",
            masterStopJob()
                .withStop(new TaskStop(TaskStop.Trigger.STALLED, 9, "3 idle rounds"))
                .withMaster(Master.defaults().withLiftedStops(1))),
        arguments("master that only judges", masterStopJob().withMode("judge")),
        arguments(
            "task the master left to the human",
            masterStopJob().withMaster(Master.defaults().withLeft("deploy"))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("neverAskedCases")
  void asksTheMasterNothingFor(String situation, TestMasterStopJobBuilder scenario) {
    state.putTask("ABC-42", scenario.task());
    when(config.load()).thenReturn(scenario.config());

    scenario.build(config, panel, moves, state, Clock.systemUTC()).runOn(state.tasks());

    verify(panel, never()).ask(anyString(), any(), any());
  }

  @Test
  void theStopStaysTheHumansWhereTheMasterAnswersHumanAndIsNotAskedAgain() {
    TestMasterStopJobBuilder scenario =
        masterStopJob()
            .withStop(new TaskStop(TaskStop.Trigger.SHIPS, 5, "4 ships"))
            .withKind(MasterStopAnswer.Kind.HUMAN)
            .withAdvice("the tree is the human's to judge");
    state.putTask("ABC-42", scenario.task());
    when(config.load()).thenReturn(scenario.config());
    when(panel.ask(anyString(), any(), any())).thenReturn(scenario.answer());
    MasterStopJob job = scenario.build(config, panel, moves, state, Clock.systemUTC());

    job.runOn(state.tasks());
    job.runOn(state.tasks());

    verify(panel, times(1)).ask(anyString(), any(), any());
    verify(moves, never()).lift(anyString());
  }

  @Test
  void theStopIsNotAskedAgainWhereTheAskFailed() {
    TestMasterStopJobBuilder scenario = masterStopJob();
    state.putTask("ABC-42", scenario.task());
    when(config.load()).thenReturn(scenario.config());
    when(panel.ask(anyString(), any(), any()))
        .thenThrow(new IllegalStateException("model unreachable"));
    MasterStopJob job = scenario.build(config, panel, moves, state, Clock.systemUTC());

    assertThatThrownBy(() -> job.runOn(state.tasks())).isInstanceOf(IllegalStateException.class);
    job.runOn(state.tasks());

    assertThat(state.task("ABC-42").orElseThrow().arbiterOrNone().askedStop()).isNotEmpty();
    verify(panel, times(1)).ask(anyString(), any(), any());
  }

  static Stream<Arguments> answeredCases() {
    return Stream.of(
        arguments(
            "lift: the master lifts it", masterStopJob(), false, times(1), atLeast(0), atLeast(0)),
        arguments(
            "return: the session is told the instruction and the task stays at work",
            masterStopJob()
                .withStop(new TaskStop(TaskStop.Trigger.UNCHANGED_FAILURE, 5, "unit-tests again"))
                .withKind(MasterStopAnswer.Kind.RETURN)
                .withAdvice("fix the fixture, not the test"),
            true,
            atLeast(0),
            atLeast(0),
            times(1)),
        arguments(
            "return on a task waiting on its plan stays the human's",
            masterStopJob()
                .withStatus(TaskStatus.PLAN_PENDING)
                .withStop(new TaskStop(TaskStop.Trigger.UNCHANGED_FAILURE, 5, "unit-tests again"))
                .withKind(MasterStopAnswer.Kind.RETURN)
                .withAdvice("fix the fixture, not the test"),
            false,
            never(),
            atLeast(0),
            atLeast(0)),
        arguments(
            "escalate: the session starts again one tier up",
            masterStopJob()
                .withStop(new TaskStop(TaskStop.Trigger.ROUNDS, 5, "3 rounds"))
                .withKind(MasterStopAnswer.Kind.ESCALATE)
                .withAdvice("sonnet looped on a fixture"),
            false,
            never(),
            times(1),
            atLeast(0)),
        arguments(
            "escalate the human kept stays the human's",
            masterStopJob()
                .withMine(List.of("escalate"))
                .withStop(new TaskStop(TaskStop.Trigger.ROUNDS, 5, "3 rounds"))
                .withKind(MasterStopAnswer.Kind.ESCALATE)
                .withAdvice("sonnet looped on a fixture"),
            false,
            atLeast(0),
            never(),
            atLeast(0)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("answeredCases")
  void actsOnTheMastersAnswerAsTheCaseAllows(
      String situation,
      TestMasterStopJobBuilder scenario,
      boolean liftWorks,
      VerificationMode lifts,
      VerificationMode escalations,
      VerificationMode returns) {
    state.putTask("ABC-42", scenario.task());
    when(config.load()).thenReturn(scenario.config());
    when(panel.ask(anyString(), any(), any())).thenReturn(scenario.answer());
    when(moves.lift(anyString())).thenReturn(liftWorks);

    scenario.build(config, panel, moves, state, Clock.systemUTC()).runOn(state.tasks());

    verify(moves, lifts).lift("ABC-42");
    verify(moves, escalations).escalate("ABC-42");
    verify(moves, returns).returned("ABC-42", scenario.advice());
  }
}
