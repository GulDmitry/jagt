package dev.jagt.orchestrator.service.plan;

import static org.assertj.core.api.Assertions.assertThat;
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
import dev.jagt.orchestrator.flow.session.Standing;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
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
            any(Standing.class)))
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
            Standing.of(state.task("ABC-1").orElseThrow()));
  }

  @Test
  void tellsTheCallerWhereTheSessionWasNotToldTheStartOnThePlan() {
    when(door.presses("ABC-1", "approve-plan")).thenReturn(true);
    when(relay.relay(
            eq("ABC-1"),
            eq(Step.PLAN),
            anyString(),
            eq(StepRelay.Delivery.CONTEXT),
            any(Standing.class)))
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
            any(Standing.class));
  }
}
