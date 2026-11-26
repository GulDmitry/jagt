package dev.jagt.orchestrator.service.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.session.SessionStanding;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.Step;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class PlanApprovalTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final StepRelay relay = mock(StepRelay.class);
  private final CaseDoor door = mock(CaseDoor.class);
  private final ApprovedPlan plan = mock(ApprovedPlan.class);

  @TempDir Path root;

  private StateService state;

  @BeforeEach
  void putsPlanPendingTask() {
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString()),
                FakeHost.machine()),
            reducer,
            event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.REQUIRED)
            .build());
  }

  @Test
  void opensThePlanGateAndTellsTheSessionToStartOnThePlan() {
    when(door.presses("ABC-1", "approve-plan")).thenReturn(true);
    when(relay.relay(
            eq("ABC-1"),
            eq(Step.PLAN),
            anyString(),
            eq(StepRelay.Delivery.CONTEXT),
            any(SessionStanding.class)))
        .thenReturn(true);

    boolean told =
        new PlanApproval(state, relay, door, plan).approve("ABC-1", "The human read plan.md");

    assertThat(told).isTrue();
    assertThat(state.task("ABC-1").orElseThrow().gate().planGate()).isEqualTo(PlanGate.OPEN);
    verify(plan).settle("ABC-1");
    verify(relay)
        .relay(
            "ABC-1",
            Step.PLAN,
            "The human read plan.md: the plan holds.",
            StepRelay.Delivery.CONTEXT,
            SessionStanding.of(state.task("ABC-1").orElseThrow()));
  }

  @Test
  void tellsTheCallerWhereTheSessionWasNotToldTheStartOnThePlan() {
    when(door.presses("ABC-1", "approve-plan")).thenReturn(true);
    when(relay.relay(
            eq("ABC-1"),
            eq(Step.PLAN),
            anyString(),
            eq(StepRelay.Delivery.CONTEXT),
            any(SessionStanding.class)))
        .thenReturn(false);

    boolean told =
        new PlanApproval(state, relay, door, plan).approve("ABC-1", "The human read plan.md");

    assertThat(told).isFalse();
  }

  @Test
  void leavesThePlanGateClosedWhereNoMasterCaseAllowsTheApproval() {

    boolean told =
        new PlanApproval(state, relay, door, plan).approve("ABC-1", "The human read plan.md");

    assertThat(told).isFalse();
    assertThat(state.task("ABC-1").orElseThrow().gate().planGate()).isEqualTo(PlanGate.REQUIRED);
    verify(plan, never()).settle(anyString());
    verify(relay, never())
        .relay(
            anyString(),
            any(Step.class),
            anyString(),
            any(StepRelay.Delivery.class),
            any(SessionStanding.class));
  }

  @Test
  void refusesTheMasterApprovalOfPlanNotHeadedSoundOnTrackerTask() {
    state.putTask(
        "ABC-2",
        TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.CLOSED)
            .openedBy(ActionOrigin.TRACKER)
            .build());
    when(door.presses("ABC-2", "approve-plan")).thenReturn(true);
    when(plan.sound("ABC-2")).thenReturn(false);

    assertThatThrownBy(() ->
            OriginContext.as(
                ActionOrigin.MASTER,
                () -> new PlanApproval(state, relay, door, plan).approve("ABC-2", "The Master")))
        .isInstanceOf(Refusal.class)
        .extracting(refusal -> ((Refusal) refusal).code())
        .isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE);
    assertThat(state.task("ABC-2").orElseThrow().gate().planGate()).isEqualTo(PlanGate.CLOSED);
    verify(plan, never()).settle("ABC-2");
  }

  @Test
  void letsTheMasterApprovePlanHeadedSoundOnTrackerTask() {
    state.putTask(
        "ABC-2",
        TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.CLOSED)
            .openedBy(ActionOrigin.TRACKER)
            .build());
    when(door.presses("ABC-2", "approve-plan")).thenReturn(true);
    when(plan.sound("ABC-2")).thenReturn(true);

    OriginContext.as(
        ActionOrigin.MASTER,
        () -> new PlanApproval(state, relay, door, plan).approve("ABC-2", "The Master"));

    assertThat(state.task("ABC-2").orElseThrow().gate().planGate()).isEqualTo(PlanGate.OPEN);
  }

  @Test
  void letsTheHumanApprovePlanNotHeadedSoundOnTrackerTask() {
    state.putTask(
        "ABC-2",
        TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.CLOSED)
            .openedBy(ActionOrigin.TRACKER)
            .build());
    when(door.presses("ABC-2", "approve-plan")).thenReturn(true);
    when(plan.sound("ABC-2")).thenReturn(false);

    OriginContext.as(
        ActionOrigin.BOARD,
        () -> new PlanApproval(state, relay, door, plan).approve("ABC-2", "The human"));

    assertThat(state.task("ABC-2").orElseThrow().gate().planGate()).isEqualTo(PlanGate.OPEN);
  }
}
