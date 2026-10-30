package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanApprovalTest {

    private final StepRelay relay = mock(StepRelay.class);
    private final CaseDoor door = mock(CaseDoor.class);

    @Test
    void opensThePlanGateAndTellsTheSessionToStartOnThePlan(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING)
                .planGate(PlanGate.REQUIRED).build());
        when(door.presses("ABC-1", "approve-plan")).thenReturn(true);
        when(relay.relay(eq("ABC-1"), eq(Step.PLAN), anyString(), any(Standing.class))).thenReturn(true);

        boolean told = new PlanApproval(state, relay, door).approve("ABC-1", "The human read plan.md");

        assertThat(told).isTrue();
        assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.OPEN);
        verify(relay).relay("ABC-1", Step.PLAN, "The human read plan.md: the plan holds.", Standing.of(state.task("ABC-1").orElseThrow()));
    }

    @Test
    void tellsTheCallerWhereTheSessionWasNotToldTheStartOnThePlan(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING)
                .planGate(PlanGate.REQUIRED).build());
        when(door.presses("ABC-1", "approve-plan")).thenReturn(true);
        when(relay.relay(eq("ABC-1"), eq(Step.PLAN), anyString(), any(Standing.class))).thenReturn(false);

        boolean told = new PlanApproval(state, relay, door).approve("ABC-1", "The human read plan.md");

        assertThat(told).isFalse();
    }

    @Test
    void leavesThePlanGateClosedWhereNoMasterCaseAllowsTheApproval(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING)
                .planGate(PlanGate.REQUIRED).build());
        when(door.presses("ABC-1", "approve-plan")).thenReturn(false);

        boolean told = new PlanApproval(state, relay, door).approve("ABC-1", "The human read plan.md");

        assertThat(told).isFalse();
        assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.REQUIRED);
        verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
    }
}
