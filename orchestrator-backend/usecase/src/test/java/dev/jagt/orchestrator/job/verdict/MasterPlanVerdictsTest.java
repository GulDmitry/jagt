package dev.jagt.orchestrator.job.verdict;

import static dev.jagt.orchestrator.job.TestRolesRootBuilder.rolesRoot;
import static dev.jagt.orchestrator.job.TestStepRelayBuilder.stepRelay;
import static dev.jagt.orchestrator.job.verdict.TestMasterClaimsBuilder.masterClaims;
import static dev.jagt.orchestrator.service.TestCaseDoorBuilder.caseDoor;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.stop.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.service.plan.ApprovedPlan;
import dev.jagt.orchestrator.service.plan.PlanApproval;
import dev.jagt.orchestrator.service.plan.Unplaceable;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.service.turn.TaskMovedOn;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasterPlanVerdictsTest {

  @TempDir Path root;

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final ApprovedPlan plan = mock(ApprovedPlan.class);
  private final MasterDecisions decisions = spy(new MasterDecisions(mock(WorktreeChanges.class)));
  private final ConfigFile.ArbiterConfig acting =
      new ConfigFile.ArbiterConfig("act", null, null, List.of(), null);
  private final ConfigFile.ArbiterConfig actingWithoutKeepList =
      new ConfigFile.ArbiterConfig("act", null, null, null, null);
  private final ConfigFile.ArbiterConfig keepingPlan =
      new ConfigFile.ArbiterConfig("act", null, null, List.of("plan"), null);
  private final MasterReview.Verdict planHolds =
      new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, "");
  private StateService state;
  private StepRelay relay;
  private MasterClaims claims;
  private CaseDoor door;
  private MasterPlanVerdicts verdicts;
  private TaskState plainPlan;

  @BeforeEach
  void setUp() throws Exception {
    state = stateService().withRoot(root).build();
    door =
        caseDoor()
            .withRoot(root)
            .build(
                state, taskStops().withRoot(root).build(state, new FakeClock()), new FakeClock());
    rolesRoot().withRoot(root).build();
    relay = stepRelay().withRoot(root).build(state, sessions, new FakeClock());
    claims = masterClaims().build(state, new FakeClock());
    plainPlan = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
    verdicts =
        new MasterPlanVerdicts(
            relay, claims, decisions, new PlanApproval(state, relay, door, plan));
  }

  @Test
  void relaysNothingOfPlanTheHumanMovedWhileTheMasterRead() throws Exception {
    TaskState read = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
    state.putTask("ABC-1", read);
    state.updateTask(
        "ABC-1",
        live ->
            live.withStatus(TaskStatus.IN_PROGRESS, null, 6_000)
                .withTurn(new Turn("plan", 6, "d", 6_000, Map.of(), List.of())));

    assertThatThrownBy(() ->
            verdicts.holds(
                "ABC-1",
                read,
                new MasterReview.Verdict(
                    MasterReview.Kind.READY, List.of(), List.of(), List.of(), 0, ""),
                new ConfigFile.ArbiterConfig("act", null, null, List.of(), null)))
        .isInstanceOf(TaskMovedOn.class);

    verify(sessions, never()).writeTaskContext(anyString(), anyString());
    assertThat(state.task("ABC-1").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void startsTheSessionOnPlanTheMasterFoundHolds() throws Exception {
    state.putTask("ABC-1", plainPlan);

    boolean moved = verdicts.holds("ABC-1", plainPlan, planHolds, acting);

    assertThat(moved).isTrue();
    assertThat(state.task("ABC-1").orElseThrow())
        .returns(TaskStatus.IN_PROGRESS, taskState -> taskState.standing().status())
        .returns("master: the plan holds", taskState -> taskState.standing().message());
  }

  @Test
  void returnsToTheSessionPlanNamingProjectJagtLacksInsteadOfAskingTheHuman() throws Exception {
    TaskState planned =
        TaskState.builder(List.of(TaskRepo.of(null, root.toString())), TaskStatus.PLAN_PENDING)
            .alias("a1")
            .build();
    state.putTask("ABC-1", planned);
    when(plan.settle("ABC-1")).thenThrow(new Unplaceable(List.of("billing"), List.of("api")));

    boolean moved = verdicts.holds("ABC-1", planned, planHolds, acting);

    assertThat(moved).isTrue();
    assertThat(state.task("ABC-1").orElseThrow())
        .returns(AgentReport.PLAIN, taskState -> taskState.standing().report())
        .returns(true, TaskState::unplaced);
    verify(sessions)
        .writeTaskContext(
            eq("ABC-1"), contains("billing, which jagt cannot place; registered: api"));
  }

  @Test
  void opensTheGateOfAnAutoplannedTaskWhenTheMasterFindsThePlanHolds() throws Exception {
    TaskState planned =
        TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.CLOSED)
            .build();
    state.putTask("ABC-1", planned);

    verdicts.holds("ABC-1", planned, planHolds, acting);

    assertThat(state.task("ABC-1").orElseThrow().gate().planGate()).isEqualTo(PlanGate.OPEN);
  }

  @Test
  void leavesPlanAskedForInPlanModeForTheHumanThoughTheMasterMayApprovePlans() throws Exception {
    TaskState planned =
        TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.REQUIRED)
            .build();
    state.putTask("ABC-1", planned);

    boolean moved = verdicts.holds("ABC-1", planned, planHolds, acting);

    assertThat(moved).isFalse();
    assertThat(state.task("ABC-1").orElseThrow())
        .returns(TaskStatus.PLAN_PENDING, taskState -> taskState.standing().status())
        .returns(PlanGate.REQUIRED, taskState -> taskState.gate().planGate());
  }

  @Test
  void leavesPlanThatHoldsForTheHumanWhoKeptThatStep() throws Exception {
    state.putTask("ABC-1", plainPlan);

    boolean moved = verdicts.holds("ABC-1", plainPlan, planHolds, keepingPlan);

    assertThat(moved).isFalse();
    verify(sessions, never()).writeTaskContext(anyString(), anyString());
  }

  @Test
  void answersThePlansQuestionWithTheDecisionBeforeStartingTheSession() throws Exception {
    TaskState planned =
        TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
            .message("admins only or every user?")
            .report(AgentReport.QUESTION)
            .build();
    state.putTask("ABC-1", planned);

    verdicts.holds(
        "ABC-1",
        planned,
        new MasterReview.Verdict(
            MasterReview.Kind.READY,
            List.of(),
            List.of(
                new MasterReview.Said(
                    "planner",
                    new Finding(
                        "",
                        "the endpoint is for admins only, as the ticket says",
                        "decided",
                        "noise",
                        "",
                        "scope"))),
            List.of(),
            1,
            ""),
        acting);

    verify(sessions).writeTaskContext(eq("ABC-1"), contains("answered your question"));
    verify(sessions)
        .writeTaskContext(
            eq("ABC-1"), contains("the endpoint is for admins only, as the ticket says"));
    verify(sessions).writeTaskContext(eq("ABC-1"), contains("the plan holds."));
  }

  @Test
  void startsNothingOnPlanTheSessionWasAlreadyToldHolds() throws Exception {
    state.putTask("ABC-1", plainPlan);

    boolean moved = verdicts.holds("ABC-1", plainPlan, planHolds, actingWithoutKeepList);

    assertThat(moved).isFalse();
    assertThat(state.task("ABC-1").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.PLAN_PENDING);
  }

  @Test
  void startsNoSessionOnPlanWhereNoMasterCaseAllowsTheApproval() throws Exception {
    Files.delete(root.resolve("roles/master/cases.md.dist"));
    state.putTask("ABC-1", plainPlan);

    boolean moved = verdicts.holds("ABC-1", plainPlan, planHolds, acting);

    assertThat(moved).isFalse();
    assertThat(state.task("ABC-1").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.PLAN_PENDING);
  }

  @Test
  void movesNothingOfPlanThatIsGoneFromTheState() throws Exception {
    TaskState task =
        TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).alias("a1").build();

    boolean moved =
        verdicts.holds(
            "ABC-1",
            task,
            new MasterReview.Verdict(
                MasterReview.Kind.READY,
                List.of(
                    new MasterReview.Said(
                        "developer",
                        new Finding("Foo.java", "wrong", "", "blocking", "", "behaviour"))),
                List.of(),
                List.of(),
                1,
                ""),
            actingWithoutKeepList);

    assertThat(moved).isFalse();
  }
}
