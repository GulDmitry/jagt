package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.port.LineChange;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanApprovalPlacementTest {

    private final StepRelay relay = mock(StepRelay.class);
    private final CaseDoor door = mock(CaseDoor.class);

    @Test
    void startsAFreshSessionInTheNewPrimaryOfATaskItsApprovalPlaced(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING).planGate(PlanGate.REQUIRED).build());
        PlanPlacement placement = mock(PlanPlacement.class);
        when(placement.place("ABC-1")).thenReturn(true);
        when(door.presses("ABC-1", "approve-plan")).thenReturn(true);
        when(relay.relayUnder(eq("ABC-1"), eq(Step.PLAN), anyString(), eq(true), any(Standing.class))).thenReturn(true);

        boolean told = new PlanApproval(state, relay, door,
                new PlanBaseline(paths, new TaskArtifacts(state), (earlier, later) -> LineChange.none()),
                new ApprovedRepos(placement, new UnaffectedRepos(state, new TaskArtifacts(state), null, null, null)))
                .approve("ABC-1", "The human read plan.md");

        assertThat(told).isTrue();
        verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
    }

    @Test
    void startsAFreshSessionInTheNewPrimaryWhereTheMasterApprovedAndPlacedTheTask(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState planned = TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING).planGate(PlanGate.REQUIRED).build();
        state.putTask("ABC-1", planned);
        PlanPlacement placement = mock(PlanPlacement.class);
        when(placement.place("ABC-1")).thenReturn(true);
        when(door.presses("ABC-1", "approve-plan")).thenReturn(true);
        when(relay.relayUnder(eq("ABC-1"), eq(Step.PLAN), anyString(), eq(true), any(StepRelay.Claim.class))).thenReturn(true);
        StepRelay.Claim claim = new StepRelay.Claim(planned, () -> true, opens -> FlowReports.Claimed.LANDED, opened -> true);

        boolean told = new PlanApproval(state, relay, door,
                new PlanBaseline(paths, new TaskArtifacts(state), (earlier, later) -> LineChange.none()),
                new ApprovedRepos(placement, new UnaffectedRepos(state, new TaskArtifacts(state), null, null, null)))
                .approve("ABC-1", "The Master read plan.md", claim);

        assertThat(told).isTrue();
        verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(StepRelay.Claim.class));
    }

    @Test
    void leavesTheGateShutAndTellsNoSessionWhereThePlanNamesWhatJagtCannotPlace(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING).planGate(PlanGate.REQUIRED).build());
        PlanPlacement placement = mock(PlanPlacement.class);
        when(placement.place("ABC-1")).thenThrow(new Unplaceable(List.of("billing"), List.of()));
        when(door.presses("ABC-1", "approve-plan")).thenReturn(true);

        assertThatThrownBy(() -> new PlanApproval(state, relay, door,
                new PlanBaseline(paths, new TaskArtifacts(state), (earlier, later) -> LineChange.none()),
                new ApprovedRepos(placement, new UnaffectedRepos(state, new TaskArtifacts(state), null, null, null)))
                .approve("ABC-1", "The human read plan.md")).isInstanceOf(Unplaceable.class);

        assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.REQUIRED);
        verify(relay, never()).relay(anyString(), any(Step.class), anyString(), any(Standing.class));
    }

    @Test
    void tellsTheCallerWhereTheFreshSessionWasNotToldTheStartOnThePlan(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING).planGate(PlanGate.REQUIRED).build());
        PlanPlacement placement = mock(PlanPlacement.class);
        when(placement.place("ABC-1")).thenReturn(true);
        when(door.presses("ABC-1", "approve-plan")).thenReturn(true);
        when(relay.relayUnder(eq("ABC-1"), eq(Step.PLAN), anyString(), eq(true), any(Standing.class))).thenReturn(false);

        boolean told = new PlanApproval(state, relay, door,
                new PlanBaseline(paths, new TaskArtifacts(state), (earlier, later) -> LineChange.none()),
                new ApprovedRepos(placement, new UnaffectedRepos(state, new TaskArtifacts(state), null, null, null)))
                .approve("ABC-1", "The human read plan.md");

        assertThat(told).isFalse();
    }

    @Test
    void tellsTheMasterWhereTheFreshSessionWasNotToldTheStartOnThePlan(@TempDir Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString()));
        StateService state = new StateService(new JsonMapper(), paths);
        TaskState planned = TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING).planGate(PlanGate.REQUIRED).build();
        state.putTask("ABC-1", planned);
        PlanPlacement placement = mock(PlanPlacement.class);
        when(placement.place("ABC-1")).thenReturn(true);
        when(door.presses("ABC-1", "approve-plan")).thenReturn(true);
        when(relay.relayUnder(eq("ABC-1"), eq(Step.PLAN), anyString(), eq(true), any(StepRelay.Claim.class))).thenReturn(false);
        StepRelay.Claim claim = new StepRelay.Claim(planned, () -> true, opens -> FlowReports.Claimed.LANDED, opened -> true);

        boolean told = new PlanApproval(state, relay, door,
                new PlanBaseline(paths, new TaskArtifacts(state), (earlier, later) -> LineChange.none()),
                new ApprovedRepos(placement, new UnaffectedRepos(state, new TaskArtifacts(state), null, null, null)))
                .approve("ABC-1", "The Master read plan.md", claim);

        assertThat(told).isFalse();
    }
}
