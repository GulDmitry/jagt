package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestCaseDoorBuilder.caseDoor;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.stop.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.CaseDoor.Admission;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.Step;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CaseDoorTest {

  @TempDir Path root;

  private StateService state;
  private CaseDoor door;

  @BeforeEach
  void wireState() {
    state = stateService().withRoot(root).build();
    door =
        caseDoor()
            .withRoot(root)
            .build(
                state,
                taskStops()
                    .withRoot(root)
                    .withProfile(
                        new ConfigFile.ArbiterConfig("act", null, null, null, null).profile())
                    .build(state, new FakeClock()),
                new FakeClock());
  }

  private static final String SHIP_CASE =
      "## ship\n\nmove: ship\nat: REVIEW_PENDING\nby: master\nThe Master ships it.\n";

  @Test
  void letsAnyPressThroughThatTheMasterDidNotMake() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    assertThat(door.presses("ABC-42", TaskAction.DONE)).isTrue();
    assertThat(door.presses("ABC-42", "ship")).isTrue();
    assertThat(CaseDoor.byTheArbiter()).isFalse();
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
  void letsTheMasterPressVerbOnceReservedForHumanWhereCaseAllowsIt(TaskAction action, String move)
      throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(
        root.resolve("roles/master/cases.md"),
        "## verb\n\nmove: " + move + "\nat: any\nby: master\nThe Master presses it.\n");
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    boolean admitted = OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", action));

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
  void refusesTheMasterVerbItsHumanKeepsInMine(TaskAction action, String mine) {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    CaseDoor door =
        caseDoor()
            .withRoot(root)
            .build(
                state,
                taskStops()
                    .withRoot(root)
                    .withProfile(
                        new ConfigFile.ArbiterConfig("act", null, null, List.of(mine), null)
                            .profile())
                    .build(state, new FakeClock()),
                new FakeClock());

    assertThatThrownBy(
            () -> OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", action)))
        .isInstanceOfSatisfying(
            Refusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
  }

  @Test
  void refusesTheMasterLaterPlanApprovalItsHumanKeepsAsReplan() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).planApproved(true).build());
    CaseDoor door =
        caseDoor()
            .withRoot(root)
            .build(
                state,
                taskStops()
                    .withRoot(root)
                    .withProfile(
                        new ConfigFile.ArbiterConfig("act", null, null, List.of("replan"), null)
                            .profile())
                    .build(state, new FakeClock()),
                new FakeClock());

    assertThatThrownBy(() ->
            OriginContext.as(
                ActionOrigin.MASTER, () -> door.presses("ABC-42", TaskAction.APPROVE_PLAN)))
        .isInstanceOfSatisfying(
            Refusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
  }

  @Test
  void letsTheMasterPressVerbCaseAllowsAtTheTasksStatus() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/cases.md"), SHIP_CASE);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());

    boolean byVerb =
        OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", TaskAction.SHIP));
    boolean byMove = OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", "ship"));

    assertThat(byVerb).isTrue();
    assertThat(byMove).isTrue();
    assertThat(state.task("ABC-42").orElseThrow().work().stopped()).isFalse();
  }

  @Test
  void stopsTheTaskWhereNoMasterCaseAllowsTheVerbAtItsStatus() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/cases.md"), SHIP_CASE);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    boolean byVerb =
        OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", TaskAction.SHIP));

    assertThat(byVerb).isFalse();
    assertThat(state.task("ABC-42").orElseThrow().work().stop())
        .extracting(TaskStop::trigger, TaskStop::evidence)
        .containsExactly(TaskStop.Trigger.MISS, "no arbiter case allows ship at IN_PROGRESS");
  }

  @Test
  void refusesTheMasterMoveNoCaseAllowsAtTheTasksStatus() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/cases.md"), SHIP_CASE);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    boolean byMove = OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", "ship"));

    assertThat(byMove).isFalse();
  }

  @Test
  void takesNoChangesFromShipTheMasterPressedOnCleanPushedTree() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../../roles/session/cases.md.dist"), root.resolve("roles/session/cases.md.dist"));
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .turn(Turn.none().relayed("ship", "d1", "", 100))
            .build());

    boolean taken =
        OriginContext.as(
            ActionOrigin.MASTER,
            () -> door.takes("ABC-42", TaskStatus.CI_POLLING, AgentReport.NO_CHANGES));

    assertThat(taken).isTrue();
    assertThat(state.task("ABC-42").orElseThrow().work().stopped()).isFalse();
  }

  @Test
  void knowsTheMasterByTheOriginTheCallRunsUnder() {
    assertThat(OriginContext.as(ActionOrigin.MASTER, CaseDoor::byTheArbiter)).isTrue();
    assertThat(OriginContext.as(ActionOrigin.BOARD, CaseDoor::byTheArbiter)).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"LIFT, lift", "ESCALATE, escalate"})
  void refusesTheMasterLiftOnceItsOneLiftSinceTheHumansLastIsSpent(TaskAction action, String move)
      throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(
        root.resolve("roles/master/cases.md"),
        "## verb\n\nmove: " + move + "\nat: any\nby: master\nThe Master presses it.\n");
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .master(Master.defaults().withLiftedStops(1))
            .build());

    assertThatThrownBy(
            () -> OriginContext.as(ActionOrigin.MASTER, () -> door.presses("ABC-42", action)))
        .isInstanceOfSatisfying(
            Refusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
  }

  @ParameterizedTest
  @CsvSource({"audit, audit, true", "acceptance, audit, false", "acceptance, acceptance, true"})
  void sendsCheckerRoundOnlyWhereCaseNamesThatChecker(
      String caseBy, String checker, boolean admitted) throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(
        root.resolve("roles/session/cases.md"),
        "## round\n\nstep: acceptance-round\nat: DEPLOYED\nby: "
            + caseBy
            + "\nreplies: IN_PROGRESS; plain\nThe checker sends its rows back.\n");
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    Admission result =
        OriginContext.asChecker(checker, () -> door.opens("ABC-42", Step.ACCEPTANCE_ROUND));

    assertThat(result.admitted()).isEqualTo(admitted);
  }
}
