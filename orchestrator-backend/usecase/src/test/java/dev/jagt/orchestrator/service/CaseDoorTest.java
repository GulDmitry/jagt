package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Master;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class CaseDoorTest {

  private static final String SHIP_CASE =
      "## ship\n\nmove: ship\nat: REVIEW_PENDING\nby: master\nThe Master ships it.\n";

  private static CaseDoor doorOver(OrchestratorPaths paths, StateService state) {
    return doorOver(
        paths, state, new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null));
  }

  private static CaseDoor doorOver(
      OrchestratorPaths paths, StateService state, ConfigService.ConfigFile.MasterConfig master) {
    return new CaseDoor(
        new SessionCases(paths),
        new MasterCases(paths),
        state,
        new MissLog(new JsonMapper(), paths, Clock.systemUTC()),
        new TaskStops(
            task -> master.profile(),
            state,
            ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
            new StopAnnouncements(
                new Notifications(List.of(), state, Runnable::run),
                new StopLog(new JsonMapper(), paths)),
            Clock.systemUTC()));
  }

  @Test
  void letsAnyPressThroughThatTheMasterDidNotMake(@TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    CaseDoor door = doorOver(paths, state);

    assertThat(door.presses("ABC-42", TaskAction.DONE)).isTrue();
    assertThat(door.presses("ABC-42", "ship")).isTrue();
    assertThat(CaseDoor.byTheMaster()).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "TRUST, trust",
    "LIFT, lift",
    "ESCALATE, escalate",
    "PAUSE, pause",
    "DONE, done",
    "REMOVE_REPO, remove-repo"
  })
  void letsTheMasterPressAVerbOnceReservedForAHumanWhereACaseAllowsIt(
      TaskAction action, String move, @TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(
        root.resolve("roles/master/cases.md"),
        "## verb\n\nmove: " + move + "\nat: any\nby: master\nThe Master presses it.\n");
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    CaseDoor door = doorOver(paths, state);

    boolean admitted =
        OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", action));

    assertThat(admitted).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "DONE, done",
    "LIFT, lift",
    "ESCALATE, escalate",
    "TRUST, trust",
    "APPROVE_PLAN, plan",
    "ADD_REPO, repo"
  })
  void refusesTheMasterAVerbItsHumanKeepsInMine(
      TaskAction action, String mine, @TempDir Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    CaseDoor door =
        doorOver(
            paths,
            state,
            new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(mine), null));

    assertThatThrownBy(
            () -> OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", action)))
        .isInstanceOfSatisfying(
            Refusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
  }

  @Test
  void letsTheMasterPressAVerbACaseAllowsAtTheTasksStatus(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/cases.md"), SHIP_CASE);
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
    CaseDoor door = doorOver(paths, state);

    boolean byVerb =
        OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", TaskAction.SHIP));
    boolean byMove = OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", "ship"));

    assertThat(byVerb).isTrue();
    assertThat(byMove).isTrue();
    assertThat(state.task("ABC-42").orElseThrow().stopped()).isFalse();
  }

  @Test
  void stopsTheTaskWhereNoMasterCaseAllowsTheVerbAtItsStatus(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/cases.md"), SHIP_CASE);
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    CaseDoor door = doorOver(paths, state);

    boolean byVerb =
        OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", TaskAction.SHIP));

    assertThat(byVerb).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().stop())
        .extracting(TaskStop::trigger, TaskStop::evidence)
        .containsExactly(TaskStop.Trigger.MISS, "no master case allows ship at IN_PROGRESS");
  }

  @Test
  void refusesTheMasterAMoveNoCaseAllowsAtTheTasksStatus(@TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/cases.md"), SHIP_CASE);
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    CaseDoor door = doorOver(paths, state);

    boolean byMove = OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", "ship"));

    assertThat(byMove).isFalse();
  }

  @Test
  void takesNoChangesFromAShipTheMasterPressedOnACleanPushedTree(@TempDir Path root)
      throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .turn(Turn.none().relayed("ship", "d1", "", 100))
            .build());
    CaseDoor door = doorOver(paths, state);

    boolean taken =
        OriginContext.as(
            ActionOrigin.MASTER,
            () -> door.takes("ABC-42", TaskStatus.CI_POLLING, AgentReport.NO_CHANGES));

    assertThat(taken).isTrue();
    assertThat(state.task("ABC-42").orElseThrow().stopped()).isFalse();
  }

  @Test
  void knowsTheMasterByTheOriginTheCallRunsUnder() {
    assertThat(OriginContext.as(ActionOrigin.MASTER, CaseDoor::byTheMaster)).isTrue();
    assertThat(OriginContext.as(ActionOrigin.BOARD, CaseDoor::byTheMaster)).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"LIFT, lift", "ESCALATE, escalate"})
  void refusesTheMasterALiftOnceItsOneLiftSinceTheHumansLastIsSpent(
      TaskAction action, String move, @TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(
        root.resolve("roles/master/cases.md"),
        "## verb\n\nmove: " + move + "\nat: any\nby: master\nThe Master presses it.\n");
    StateService state = new StateService(new JsonMapper(), paths);
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .master(Master.defaults().withLiftedStops(1))
            .build());
    CaseDoor door = doorOver(paths, state);

    assertThatThrownBy(
            () -> OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", action)))
        .isInstanceOfSatisfying(
            Refusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
  }
}
