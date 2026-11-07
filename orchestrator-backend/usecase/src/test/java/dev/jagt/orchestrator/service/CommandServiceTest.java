package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class CommandServiceTest {

  private final FlowEngine flow = mock(FlowEngine.class);
  private final CaseDoor door = mock(CaseDoor.class);
  private final CommandService commands = new CommandService(flow, door);

  @Test
  void handsBackWhateverTheFlowSaidWithoutRewordingIt() {
    when(door.presses("ABC-1", TaskAction.SHIP)).thenReturn(true);
    when(flow.run("ABC-1", TaskAction.SHIP, null)).thenReturn("ship ABC-1: approval relayed");

    assertThat(commands.execute("ABC-1", TaskAction.SHIP))
        .isEqualTo("ship ABC-1: approval relayed");
  }

  @Test
  void handsTheProjectAHumanTypedToTheFlowAlongWithTheAction() {
    when(door.presses("ABC-1", TaskAction.ADD_REPO)).thenReturn(true);

    commands.execute("ABC-1", TaskAction.ADD_REPO, "beta");

    verify(flow).run("ABC-1", TaskAction.ADD_REPO, "beta");
  }

  @Test
  void passesAnAliasDownUntouchedSoOnlyOnePlaceResolvesIt() {
    when(door.presses("a1", TaskAction.FOCUS)).thenReturn(true);

    commands.execute("a1", TaskAction.FOCUS);

    verify(flow).run("a1", TaskAction.FOCUS, null);
  }

  @Test
  void letsARefusalReachTheCallerWithTheCodeItMustActOn() {
    when(door.presses("ABC-1", TaskAction.DEPLOY)).thenReturn(true);
    when(flow.run("ABC-1", TaskAction.DEPLOY, null))
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
  void refusesTheMasterAVerbOnlyAHumanPresses(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths);
    CommandService guarded =
        new CommandService(
            flow,
            new CaseDoor(
                new SessionCases(paths),
                new MasterCases(paths),
                state,
                new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
                new TaskStops(
                    ignored -> Arbiter.Profile.OFF,
                    state,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths)),
                    Clock.systemUTC())));

    assertThatThrownBy(
            () ->
                OriginContext.as(
                    ActionOrigin.MASTER, () -> guarded.execute("ABC-1", TaskAction.DONE)))
        .isInstanceOf(Refusal.class)
        .hasMessage("Done is a human's to press, never the Master's.");
  }

  @Test
  void refusesRecordsAndStopsAMasterPressNoMasterCaseAllows(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(
        root.resolve("roles/master/cases.md"),
        "## deploy\n\n"
            + "move: deploy\n"
            + "at: APPROVED\n"
            + "by: master\n"
            + "Green and approved, the Master deploys it.\n");
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEWED).build());
    CommandService guarded =
        new CommandService(
            flow,
            new CaseDoor(
                new SessionCases(paths),
                new MasterCases(paths),
                state,
                new MissLog(
                    new JsonMapper(),
                    paths,
                    Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC)),
                new TaskStops(
                    ignored -> Arbiter.Profile.OFF,
                    state,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths)),
                    Clock.systemUTC())));

    OriginContext.as(ActionOrigin.MASTER, () -> guarded.execute("ABC-1", TaskAction.DEPLOY));

    verify(flow, never()).run("ABC-1", TaskAction.DEPLOY);
    assertThat(Files.readString(root.resolve("misses.jsonl")))
        .isEqualTo(
            "{\"at\":1000,\"task\":\"ABC-1\",\"step\":\"deploy\",\"status\":\"REVIEWED\",\"by\":\"master\"}\n");
    assertThat(state.task("ABC-1").orElseThrow().stop())
        .extracting(TaskStop::trigger, TaskStop::evidence)
        .containsExactly(TaskStop.Trigger.MISS, "no master case allows deploy at REVIEWED");
  }

  @Test
  void saysWhyAPressTheMasterCaseDoesNotAllowWasNotRun() {
    when(door.presses("ABC-1", TaskAction.SHIP)).thenReturn(false);

    String said = commands.execute("ABC-1", TaskAction.SHIP);

    assertThat(said)
        .isEqualTo("ship ABC-1: no master case allows it at this status; see misses.jsonl");
    verify(flow, never()).run("ABC-1", TaskAction.SHIP);
  }
}
