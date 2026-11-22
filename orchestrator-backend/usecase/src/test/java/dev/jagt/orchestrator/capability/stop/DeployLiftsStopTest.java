package dev.jagt.orchestrator.capability.stop;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.stop.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DeployLiftsStopTest {

  @TempDir Path root;

  final Clock clock = Clock.fixed(Instant.ofEpochMilli(100_000_000), ZoneOffset.UTC);
  StateService state;
  TaskStops stops;

  @BeforeEach
  void stopsOverStateInTheTempRoot() {
    state = stateService().withRoot(root).build();
    stops = taskStops().withRoot(root).build(state, clock);
  }

  @ParameterizedTest
  @EnumSource(
      value = ActionOrigin.class,
      names = {"BOARD", "PALETTE"})
  void deployedTaskIsCheckedAgainWhenHumanDeployedItPastStop(ActionOrigin human) {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).build());
    stops.stop("ABC-42", TaskStop.Trigger.SHIPS, "ships without a new note");
    DeployLiftsStop lifter = new DeployLiftsStop(stops);

    OriginContext.as(
        human,
        () ->
            lifter.around(
                "ABC-42",
                () -> {
                  state.updateTask(
                      "ABC-42",
                      task -> task.withStatus(TaskStatus.DEPLOYED, "deployed", 100_000_000));
                  return Outcome.ok("deployed");
                }));

    assertThat(state.task("ABC-42").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(state.task("ABC-42").orElseThrow().stopped()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = ActionOrigin.class,
      names = {"MASTER", "JAGT", "MCP", "AUTO_REVIEW", "TRACKER"})
  void deployedTaskKeepsItsStopWhenNoHumanDeployedIt(ActionOrigin other) {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).build());
    stops.stop("ABC-42", TaskStop.Trigger.SHIPS, "ships without a new note");
    DeployLiftsStop lifter = new DeployLiftsStop(stops);

    OriginContext.as(other, () -> lifter.around("ABC-42", () -> Outcome.ok("deployed")));

    assertThat(state.task("ABC-42").orElseThrow().stopped()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ActionOrigin.class,
      names = {"BOARD", "PALETTE"})
  void humansDeployRestoresTheMastersLiftWhereNoStopStands(ActionOrigin human) {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEWED)
            .master(Master.defaults().withLiftedStops(1))
            .build());
    DeployLiftsStop lifter = new DeployLiftsStop(stops);

    OriginContext.as(human, () -> lifter.around("ABC-42", () -> Outcome.ok("deployed")));

    assertThat(state.task("ABC-42").orElseThrow().arbiterOrNone().liftedStops()).isZero();
  }
}
