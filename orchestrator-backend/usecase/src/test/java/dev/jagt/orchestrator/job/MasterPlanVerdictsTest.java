package dev.jagt.orchestrator.job;

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

import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ApprovedPlan;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.PlanApproval;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskMovedOn;
import dev.jagt.orchestrator.service.Unplaceable;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
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
  private final ConfigService.ConfigFile.MasterConfig acting =
      new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);
  private final ConfigService.ConfigFile.MasterConfig actingWithoutKeepList =
      new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
  private final ConfigService.ConfigFile.MasterConfig keepingPlan =
      new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("plan"), null);
  private final MasterReview.Verdict planHolds =
      new MasterReview.Verdict(
          MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, "");
  private StateService state;
  private MasterPlanVerdicts verdicts;
  private TaskState plainPlan;

  @BeforeEach
  void setUp() throws Exception {
    MasterVerdictsFixture fixture = new MasterVerdictsFixture(root, sessions);
    state = fixture.state;
    plainPlan = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
    verdicts =
        new MasterPlanVerdicts(
            fixture.relay,
            fixture.claims,
            decisions,
            new PlanApproval(state, fixture.relay, fixture.door, plan));
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

    assertThatThrownBy(
            () ->
                verdicts.holds(
                    "ABC-1",
                    read,
                    new MasterReview.Verdict(
                        MasterReview.Kind.READY, List.of(), List.of(), List.of(), 0, ""),
                    new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null)))
        .isInstanceOf(TaskMovedOn.class);

    verify(sessions, never()).writeTaskContext(anyString(), anyString());
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void startsTheSessionOnPlanTheMasterFoundHolds() throws Exception {
    state.putTask("ABC-1", plainPlan);

    boolean moved =
        verdicts.holds(
            "ABC-1",
            plainPlan,
            planHolds,
            acting);

    assertThat(moved).isTrue();
    assertThat(state.task("ABC-1").orElseThrow())
        .returns(TaskStatus.IN_PROGRESS, TaskState::status)
        .returns("master: the plan holds", TaskState::message);
  }

  @Test
  void returnsToTheSessionPlanNamingProjectJagtLacksInsteadOfAskingTheHuman() throws Exception {
    TaskState planned =
        TaskState.builder(List.of(TaskRepo.of(null, root.toString())), TaskStatus.PLAN_PENDING)
            .alias("a1")
            .build();
    state.putTask("ABC-1", planned);
    when(plan.settle("ABC-1")).thenThrow(new Unplaceable(List.of("billing"), List.of("api")));

    boolean moved =
        verdicts.holds(
            "ABC-1",
            planned,
            planHolds,
            acting);

    assertThat(moved).isTrue();
    assertThat(state.task("ABC-1").orElseThrow())
        .returns(AgentReport.PLAIN, TaskState::report)
        .returns(true, TaskState::unplaced);
    verify(sessions)
        .writeTaskContext(
            eq("ABC-1"), contains("billing, which jagt cannot place; registered: api"));
  }

  @Test
  void opensTheGateOfAnAutoplannedTaskWhenTheMasterFindsThePlanHolds()
      throws Exception {
    TaskState planned =
        TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.CLOSED)
            .build();
    state.putTask("ABC-1", planned);

    verdicts.holds(
        "ABC-1",
        planned,
        planHolds,
        acting);

    assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.OPEN);
  }

  @Test
  void leavesPlanAskedForInPlanModeForTheHumanThoughTheMasterMayApprovePlans()
      throws Exception {
    TaskState planned =
        TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.REQUIRED)
            .build();
    state.putTask("ABC-1", planned);

    boolean moved =
        verdicts.holds(
            "ABC-1",
            planned,
            planHolds,
            acting);

    assertThat(moved).isFalse();
    assertThat(state.task("ABC-1").orElseThrow())
        .returns(TaskStatus.PLAN_PENDING, TaskState::status)
        .returns(PlanGate.REQUIRED, TaskState::planGate);
  }

  @Test
  void leavesPlanThatHoldsForTheHumanWhoKeptThatStep() throws Exception {
    state.putTask("ABC-1", plainPlan);

    boolean moved =
        verdicts.holds(
            "ABC-1",
            plainPlan,
            planHolds,
            keepingPlan);

    assertThat(moved).isFalse();
    verify(sessions, never()).writeTaskContext(anyString(), anyString());
  }

  @Test
  void answersThePlansQuestionWithTheDecisionBeforeStartingTheSession()
      throws Exception {
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

    boolean moved =
        verdicts.holds(
            "ABC-1",
            plainPlan,
            planHolds,
            actingWithoutKeepList);

    assertThat(moved).isFalse();
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.PLAN_PENDING);
  }

  @Test
  void startsNoSessionOnPlanWhereNoMasterCaseAllowsTheApproval() throws Exception {
    Files.delete(root.resolve("roles/master/cases.md.dist"));
    state.putTask("ABC-1", plainPlan);

    boolean moved =
        verdicts.holds(
            "ABC-1",
            plainPlan,
            planHolds,
            acting);

    assertThat(moved).isFalse();
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.PLAN_PENDING);
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
