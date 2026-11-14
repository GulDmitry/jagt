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
import dev.jagt.orchestrator.flow.FlowReports;
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
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class PlanApprovalPlacementTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final StepRelay relay = mock(StepRelay.class);
  private final CaseDoor door = mock(CaseDoor.class);

  @TempDir Path root;

  private final ApprovedPlan plan = mock(ApprovedPlan.class);
  private final TaskState planned =
      TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING).planGate(PlanGate.REQUIRED).build();
  private final StepRelay.Claim claim =
      new StepRelay.Claim(
          Standing.of(planned), opens -> FlowReports.Claimed.LANDED, opened -> true);
  private StateService state;

  @BeforeEach
  void putsPlanPendingTaskTheMasterAndTheHumanMayApprove() {
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer,
            event -> {});
    state.putTask("ABC-1", planned);
    when(plan.settle("ABC-1")).thenReturn(true);
    when(door.presses("ABC-1", "approve-plan")).thenReturn(true);
  }

  @Test
  void startsFreshSessionInTheNewPrimaryOfTaskItsApprovalPlaced() {
    when(relay.relayUnder(eq("ABC-1"), eq(Step.PLAN), anyString(), eq(true), any(Standing.class)))
        .thenReturn(true);

    boolean told =
        new PlanApproval(state, relay, door, plan).approve("ABC-1", "The human read plan.md");

    assertThat(told).isTrue();
    verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void startsFreshSessionInTheNewPrimaryWhereTheMasterApprovedAndPlacedTheTask() {
    when(relay.relayUnder(
            eq("ABC-1"), eq(Step.PLAN), anyString(), eq(true), any(StepRelay.Claim.class)))
        .thenReturn(true);
    boolean told =
        new PlanApproval(state, relay, door, plan)
            .approve("ABC-1", "The Master read plan.md", claim);

    assertThat(told).isTrue();
    verify(relay, never())
        .relay(anyString(), any(Step.class), anyString(), any(StepRelay.Claim.class));
  }

  @Test
  void leavesTheGateShutAndTellsNoSessionWhereThePlanNamesWhatJagtCannotPlace() {
    when(plan.settle("ABC-1")).thenThrow(new Unplaceable(List.of("billing"), List.of()));

    assertThatThrownBy(() ->
            new PlanApproval(state, relay, door, plan).approve("ABC-1", "The human read plan.md"))
        .isInstanceOf(Unplaceable.class);

    assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.REQUIRED);
    verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
  }

  @Test
  void tellsTheCallerWhereTheFreshSessionWasNotToldTheStartOnThePlan() {
    when(relay.relayUnder(eq("ABC-1"), eq(Step.PLAN), anyString(), eq(true), any(Standing.class)))
        .thenReturn(false);

    boolean told =
        new PlanApproval(state, relay, door, plan).approve("ABC-1", "The human read plan.md");

    assertThat(told).isFalse();
  }

  @Test
  void tellsTheMasterWhereTheFreshSessionWasNotToldTheStartOnThePlan() {
    when(relay.relayUnder(
            eq("ABC-1"), eq(Step.PLAN), anyString(), eq(true), any(StepRelay.Claim.class)))
        .thenReturn(false);
    boolean told =
        new PlanApproval(state, relay, door, plan)
            .approve("ABC-1", "The Master read plan.md", claim);

    assertThat(told).isFalse();
  }
}
