package dev.jagt.orchestrator.service;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanApprovalTest {

    private final AgentSessions sessions = mock(AgentSessions.class);

    @Test
    void opensThePlanGateAndTellsTheSessionToStartOnThePlan(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING)
                .planGate(PlanGate.REQUIRED).build());
        when(sessions.relayIfChanged(eq("ABC-1"), anyString())).thenReturn(true);

        boolean told = new PlanApproval(state, sessions).approve("ABC-1", "The human read plan.md");

        assertThat(told).isTrue();
        assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.OPEN);
        verify(sessions).relayIfChanged(eq("ABC-1"), contains("the plan holds. Start on it."));
    }
}
