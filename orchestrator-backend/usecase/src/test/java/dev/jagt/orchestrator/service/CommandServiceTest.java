package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestCaseDoorBuilder.caseDoor;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.stop.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.Pressed;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.MasterMode;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandServiceTest {

  private final FlowEngine flow = mock(FlowEngine.class);
  private final CaseDoor door = mock(CaseDoor.class);
  private final CommandService commands = new CommandService(flow, door);

  @Test
  void handsBackWhateverTheFlowSaidWithoutRewordingIt() {
    when(door.presses("ABC-1", TaskAction.SHIP)).thenReturn(true);
    when(flow.press("ABC-1", TaskAction.SHIP, null))
        .thenReturn(new Pressed("ship ABC-1: approval relayed", Outcome.Kind.OK));

    assertThat(commands.execute("ABC-1", TaskAction.SHIP))
        .isEqualTo("ship ABC-1: approval relayed");
  }

  @Test
  void handsBackHowTheWorkEndedBesideWhatTheFlowSaid() {
    when(door.presses("ABC-1", TaskAction.SWEEP)).thenReturn(true);
    when(flow.press("ABC-1", TaskAction.SWEEP, null))
        .thenReturn(new Pressed("sweep ABC-1: unchanged", Outcome.Kind.NOTHING));

    assertThat(commands.press("ABC-1", TaskAction.SWEEP, null).kind())
        .isEqualTo(Outcome.Kind.NOTHING);
  }

  @Test
  void handsTheProjectHumanTypedToTheFlowAlongWithTheAction() {
    when(door.presses("ABC-1", TaskAction.ADD_REPO)).thenReturn(true);
    when(flow.press("ABC-1", TaskAction.ADD_REPO, "beta"))
        .thenReturn(new Pressed("added", Outcome.Kind.OK));

    commands.execute("ABC-1", TaskAction.ADD_REPO, "beta");

    verify(flow).press("ABC-1", TaskAction.ADD_REPO, "beta");
  }

  @Test
  void handsTheLineHumanTypedToTheFlowApartFromAnyProject() {
    when(door.presses("ABC-1", TaskAction.RUN)).thenReturn(true);
    when(flow.runLine("ABC-1", TaskAction.RUN, "ls -la")).thenReturn("ran ls -la");

    assertThat(commands.executeLine("ABC-1", TaskAction.RUN, "ls -la")).isEqualTo("ran ls -la");
  }

  @Test
  void leavesTheLineUnrunWhereNoMasterCaseAllowsIt() {
    when(door.presses("ABC-1", TaskAction.RUN)).thenReturn(false);

    assertThat(commands.executeLine("ABC-1", TaskAction.RUN, "ls -la"))
        .contains("no arbiter case allows it");
    verify(flow, never()).runLine("ABC-1", TaskAction.RUN, "ls -la");
  }

  @Test
  void passesAnAliasDownUntouchedSoOnlyOnePlaceResolvesIt() {
    when(door.presses("a1", TaskAction.FOCUS)).thenReturn(true);
    when(flow.press("a1", TaskAction.FOCUS, null))
        .thenReturn(new Pressed("focused", Outcome.Kind.OK));

    commands.execute("a1", TaskAction.FOCUS);

    verify(flow).press("a1", TaskAction.FOCUS, null);
  }

  @Test
  void letsRefusalReachTheCallerWithTheCodeItMustActOn() {
    when(door.presses("ABC-1", TaskAction.DEPLOY)).thenReturn(true);
    when(flow.press("ABC-1", TaskAction.DEPLOY, null))
        .thenThrow(
            new Refusal(
                Refusal.Code.ACTION_NOT_AVAILABLE,
                "Deploy is not available for ABC-1 (it is IN_PROGRESS — agent working)"));

    assertThatThrownBy(() -> commands.execute("ABC-1", TaskAction.DEPLOY))
        .asInstanceOf(type(Refusal.class))
        .extracting(Refusal::code)
        .isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE);
  }

  @Test
  void refusesTheMasterVerbItsHumanKeepsInMine(@TempDir Path root) {
    StateService state = stateService().withRoot(root).build();
    TaskStops stops =
        taskStops()
            .withRoot(root)
            .withProfile(new Arbiter.Profile(MasterMode.ACT, Set.of("done")))
            .build(state, new FakeClock());
    CaseDoor door = caseDoor().withRoot(root).build(state, stops, new FakeClock());
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    CommandService guarded = new CommandService(flow, door);

    assertThatThrownBy(() ->
            OriginContext.as(ActionOrigin.MASTER, () -> guarded.execute("ABC-1", TaskAction.DONE)))
        .isInstanceOf(Refusal.class)
        .hasMessage("Done is kept for the human: mine names it.");
  }

  @Test
  void refusesRecordsAndStopsMasterPressNoMasterCaseAllows(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(
        root.resolve("roles/master/cases.md"),
        """
        ## deploy

        move: deploy
        at: APPROVED
        by: master
        Green and approved, the Master deploys it.
        """);
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC);
    StateService state = stateService().withRoot(root).build();
    CaseDoor door =
        caseDoor()
            .withRoot(root)
            .build(state, taskStops().withRoot(root).build(state, clock), clock);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).build());
    CommandService guarded = new CommandService(flow, door);

    OriginContext.as(ActionOrigin.MASTER, () -> guarded.execute("ABC-1", TaskAction.DEPLOY));

    verify(flow, never()).press("ABC-1", TaskAction.DEPLOY, null);
    assertThat(Files.readString(root.resolve("records/misses.jsonl")))
        .isEqualTo(
            "{\"at\":1000,\"task\":\"ABC-1\",\"step\":\"deploy\",\"status\":\"REVIEWED\",\"by\""
                + ":\"master\"}\n");
    assertThat(state.task("ABC-1").orElseThrow().work().stop())
        .extracting(TaskStop::trigger, TaskStop::evidence)
        .containsExactly(TaskStop.Trigger.MISS, "no arbiter case allows deploy at REVIEWED");
  }

  @Test
  void saysWhyPressTheMasterCaseDoesNotAllowWasNotRun() {
    when(door.presses("ABC-1", TaskAction.SHIP)).thenReturn(false);

    String said = commands.execute("ABC-1", TaskAction.SHIP);

    assertThat(said)
        .isEqualTo("ship ABC-1: no arbiter case allows it at this status; see misses.jsonl");
    verify(flow, never()).press("ABC-1", TaskAction.SHIP, null);
  }
}
