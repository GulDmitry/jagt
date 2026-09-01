package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolGateControllerTest {

    private final StateService stateService = mock(StateService.class);

    @Test
    void refusesAPushToAnotherBranchInTheShapeTheModelReads() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        Map<String, Object> answered = new AgentToolGateController(stateService).gate("/wt/ABC-1-proj",
                new AgentToolGateController.ToolCall("Bash", Map.of("command", "git push origin dev")))
                .getBody();

        assertThat(answered).containsEntry("continue", true);
        assertThat(answered.get("hookSpecificOutput")).asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.map(String.class, Object.class))
                .containsEntry("permissionDecision", "deny")
                .containsEntry("hookEventName", "PreToolUse")
                .hasEntrySatisfying("permissionDecisionReason",
                        reason -> assertThat(reason).asString().contains("ABC-1"));
    }

    @Test
    void answersAPushOfTheTasksOwnBranchWithNothing() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        var answered = new AgentToolGateController(stateService).gate("/wt/ABC-1-proj",
                new AgentToolGateController.ToolCall("Bash", Map.of("command", "git push origin ABC-1")));

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void refusesNothingFromADirectoryNoTaskOwns() {
        when(stateService.findByWorktree("/elsewhere")).thenReturn(Optional.empty());

        var answered = new AgentToolGateController(stateService).gate("/elsewhere",
                new AgentToolGateController.ToolCall("Bash", Map.of("command", "git push origin dev")));

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void answersACallWithNoPayloadWithNothing() {
        var answered = new AgentToolGateController(stateService).gate("/wt/ABC-1-proj", null);

        assertThat(answered.getBody()).isNull();
    }
}
