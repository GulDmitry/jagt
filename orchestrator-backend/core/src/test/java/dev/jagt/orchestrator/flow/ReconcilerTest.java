package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.jagt.orchestrator.flow.Reconciler.Decision;
import dev.jagt.orchestrator.flow.deploy.Pipeline;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.MasterMode;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReconcilerTest {

  private final TaskChart chart = new TaskChart(Composition.builtIn());
  private final Reconciler reconciler =
      new Reconciler(
          chart.composition(),
          new Arbiter.Profile(MasterMode.ACT, Set.of("plan", "deploy")),
          Duration.ofMinutes(1));

  static Stream<Arguments> decisions() {
    ProgressStep turn = new ProgressStep.SessionTurn();
    ProgressStep read = new ProgressStep.HostRead();
    ProgressStep deploy = new ProgressStep.Deploy();
    ProgressStep round = new ProgressStep.JudgeRound();
    return Stream.of(
        arguments(
            "a closed task has nothing in flight",
            Map.of("lifecycle", "DONE"),
            Pipeline.UNKNOWN,
            Map.of(),
            60_000L,
            new Decision.Stay()),
        arguments(
            "a fresh session beat waits",
            Map.of("lifecycle", "IN_PROGRESS"),
            Pipeline.UNKNOWN,
            Map.of(turn, new Beat(turn, 0, 0, 0)),
            59_999L,
            new Decision.Stay()),
        arguments(
            "a missed session beat nudges first",
            Map.of("lifecycle", "IN_PROGRESS"),
            Pipeline.UNKNOWN,
            Map.of(turn, new Beat(turn, 0, 0, 0)),
            60_000L,
            new Decision.Retry(turn)),
        arguments(
            "a missed session beat past its nudge relays",
            Map.of("lifecycle", "IN_PROGRESS"),
            Pipeline.UNKNOWN,
            Map.of(turn, new Beat(turn, 0, 0, 1)),
            60_000L,
            new Decision.Restart(turn)),
        arguments(
            "a missed session beat past its relay stops undelivered",
            Map.of("lifecycle", "IN_PROGRESS"),
            Pipeline.UNKNOWN,
            Map.of(turn, new Beat(turn, 0, 0, 2)),
            60_000L,
            new Decision.Stop(
                turn,
                new TaskMove.StopSet(
                    new TaskStop(TaskStop.Trigger.UNDELIVERED, 60_000L, "a step never landed")))),
        arguments(
            "a session beat waits while a checker reads",
            Map.of("lifecycle", "IN_PROGRESS", "acceptance", "READING"),
            Pipeline.UNKNOWN,
            Map.of(turn, new Beat(turn, 0, 0, 0)),
            60_000L,
            new Decision.Stay()),
        arguments(
            "red checks on a failed request land nothing new",
            Map.of("lifecycle", "CI_FAILED", "request", "OPEN", "checks", "READ"),
            Pipeline.RED,
            Map.of(read, new Beat(read, 0, 0, 0)),
            0L,
            new Decision.Stay()),
        arguments(
            "a task stopped on a host read keeps no session beat",
            Map.of("lifecycle", "IN_PROGRESS", "stop", "HOST_READ"),
            Pipeline.UNKNOWN,
            Map.of(turn, new Beat(turn, 0, 0, 0)),
            60_000L,
            new Decision.Stay()),
        arguments(
            "green checks on the head of a polled request land reviewed",
            Map.of("lifecycle", "CI_POLLING", "request", "OPEN", "checks", "READ"),
            Pipeline.GREEN,
            Map.of(read, new Beat(read, 0, 0, 0)),
            0L,
            new Decision.Land(read, new TaskMove.Read(TaskStatus.REVIEWED))),
        arguments(
            "a polled request read on another head lands nothing",
            Map.of("lifecycle", "CI_POLLING", "request", "OPEN", "checks", "STALE"),
            Pipeline.GREEN,
            Map.of(read, new Beat(read, 0, 0, 0)),
            0L,
            new Decision.Stay()),
        arguments(
            "the judge round the master holds turns to the human once its retry is spent",
            Map.of("lifecycle", "PLAN_PENDING"),
            Pipeline.UNKNOWN,
            Map.of(round, new Beat(round, 0, 0, 1)),
            60_000L,
            new Decision.Stop(
                round,
                new TaskMove.MasterNoted(new MasterNote.Left("judge round"), MasterPhase.LEFT))),
        arguments(
            "a red deploy read past its retry lands the conflict",
            Map.of("lifecycle", "DEPLOYED", "deploy", "LANDING"),
            Pipeline.RED,
            Map.of(deploy, new Beat(deploy, 0, 0, 1)),
            0L,
            new Decision.Land(deploy, new TaskMove.DeployRead(true))),
        arguments(
            "a first red deploy read waits for its beat",
            Map.of("lifecycle", "DEPLOYED", "deploy", "LANDING"),
            Pipeline.RED,
            Map.of(deploy, new Beat(deploy, 0, 0, 0)),
            0L,
            new Decision.Stay()),
        arguments(
            "a green deploy read leaves nothing in flight",
            Map.of("lifecycle", "DEPLOYED", "deploy", "LANDING"),
            Pipeline.GREEN,
            Map.of(deploy, new Beat(deploy, 0, 0, 1)),
            60_000L,
            new Decision.Stay()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("decisions")
  void decidesWhatTheStoredFactsCallFor(
      String fact,
      Map<String, String> regions,
      Pipeline pipeline,
      Map<ProgressStep, Beat> beats,
      long now,
      Decision expected) {
    Map<String, String> plain = new HashMap<>(chart.chart().initial().toPlain());
    plain.putAll(regions);
    Reconciler.Stored task =
        new Reconciler.Stored(chart.chart().configuration(plain), pipeline, pipeline, beats);

    Decision decided = reconciler.decide(task, now);

    assertThat(decided).isEqualTo(expected);
  }

  static Stream<Arguments> stops() {
    return Stream.of(
        arguments(new ProgressStep.Verifying(), TaskStop.Trigger.UNCHANGED_FAILURE),
        arguments(new ProgressStep.HostRead(), TaskStop.Trigger.CHECKS),
        arguments(new ProgressStep.InfraRerun(), TaskStop.Trigger.INFRA),
        arguments(new ProgressStep.Deploy(), TaskStop.Trigger.DEPLOY_UNREAD));
  }

  @ParameterizedTest
  @MethodSource("stops")
  void anExhaustedStepStopsOnTheTriggerOfItsRow(ProgressStep step, TaskStop.Trigger trigger) {
    TaskMove stop = StepPolicy.of(step).stop(5L);

    assertThat(stop).isEqualTo(new TaskMove.StopSet(new TaskStop(trigger, 5L, trigger.reason())));
  }
}
