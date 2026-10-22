package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.task.Guard;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolGateControllerTest {

    private final StateService stateService = mock(StateService.class);

    @Test
    void refusesAPushToAnotherBranchInTheShapeTheModelReads() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        Map<String, Object> answered = new AgentToolGateController(stateService).gate("/wt/ABC-1-proj",
                new AgentToolGateController.ToolCall("Bash", Map.of("command", "git push origin dev")),
                new MockHttpServletRequest())
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
    void countsARefusedLineOnItsTask() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        new AgentToolGateController(stateService).gate("/wt/ABC-1-proj",
                new AgentToolGateController.ToolCall("Bash", Map.of("command", "git push origin dev")),
                new MockHttpServletRequest());

        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        assertThat(write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build())
                .refusals()).containsExactly(Map.entry(Guard.SESSION_LINE, 1));
    }

    @Test
    void answersAPushOfTheTasksOwnBranchWithNothing() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        var answered = new AgentToolGateController(stateService).gate("/wt/ABC-1-proj",
                new AgentToolGateController.ToolCall("Bash", Map.of("command", "git push origin ABC-1")),
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void refusesNothingFromADirectoryNoTaskOwns() {
        when(stateService.findByWorktree("/elsewhere")).thenReturn(Optional.empty());

        var answered = new AgentToolGateController(stateService).gate("/elsewhere",
                new AgentToolGateController.ToolCall("Bash", Map.of("command", "git push origin dev")),
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void answersNothingForAToolThatCannotPush() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        var answered = new AgentToolGateController(stateService).gate("/wt/ABC-1-proj",
                new AgentToolGateController.ToolCall("Read", Map.of("command", "git push origin dev")),
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void answersACallWithNoPayloadWithNothing() {
        var answered = new AgentToolGateController(stateService).gate("/wt/ABC-1-proj", null,
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void refusesASourceFileWriteWhileThePlanIsNotApprovedAndCountsIt() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                        .planGate(PlanGate.REQUIRED).build())));

        var answered = new AgentToolGateController(stateService).gate("/wt/ABC-1-proj",
                new AgentToolGateController.ToolCall("Write", Map.of("file_path", "/wt/ABC-1-proj/src/Foo.java")),
                new MockHttpServletRequest());

        assertThat(answered.getBody().get("hookSpecificOutput")).asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.map(String.class, Object.class))
                .containsEntry("permissionDecision", "deny")
                .hasEntrySatisfying("permissionDecisionReason",
                        reason -> assertThat(reason).asString().contains("plan first"));
        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        assertThat(write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build())
                .refusals()).containsExactly(Map.entry(Guard.PLAN_FIRST, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Edit", "MultiEdit", "Write"})
    void letsThePlanAndTheVisualThroughWhileThePlanIsNotApproved(String tool) {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                        .planGate(PlanGate.REQUIRED).build())));

        var plan = new AgentToolGateController(stateService).gate("/wt/ABC-1-proj",
                new AgentToolGateController.ToolCall(tool, Map.of("file_path", "/wt/ABC-1-proj/plan.md")),
                new MockHttpServletRequest());
        var visual = new AgentToolGateController(stateService).gate("/wt/ABC-1-proj",
                new AgentToolGateController.ToolCall(tool, Map.of("file_path", "/wt/ABC-1-proj/task_visual.md")),
                new MockHttpServletRequest());

        assertThat(plan.getBody()).isNull();
        assertThat(visual.getBody()).isNull();
    }

    @Test
    void refusesANotebookEditWhileThePlanIsNotApproved() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS)
                        .planGate(PlanGate.CLOSED).build())));

        var answered = new AgentToolGateController(stateService).gate("/wt/ABC-1-proj",
                new AgentToolGateController.ToolCall("NotebookEdit",
                        Map.of("notebook_path", "/wt/ABC-1-proj/analysis.ipynb")),
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNotNull();
    }

    @Test
    void letsEveryWriteThroughOnceThePlanIsApproved() {
        when(stateService.findByWorktree("/wt/ABC-1-proj")).thenReturn(Optional.of(Map.entry("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1-proj", TaskStatus.IN_PROGRESS).build())));

        var answered = new AgentToolGateController(stateService).gate("/wt/ABC-1-proj",
                new AgentToolGateController.ToolCall("Edit", Map.of("file_path", "/wt/ABC-1-proj/src/Foo.java")),
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void refusesNoWriteFromADirectoryNoTaskOwns() {
        when(stateService.findByWorktree("/elsewhere")).thenReturn(Optional.empty());

        var answered = new AgentToolGateController(stateService).gate("/elsewhere",
                new AgentToolGateController.ToolCall("Write", Map.of("file_path", "/elsewhere/Foo.java")),
                new MockHttpServletRequest());

        assertThat(answered.getBody()).isNull();
    }
}
