package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.port.TaskCapability;
import dev.jagt.orchestrator.port.TaskCapability;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The gate, the doer and the transition in one place: what the rules refuse, and what the task is left saying
 * afterwards. WHICH statuses allow which action is {@code FlowRulesTest}'s subject, not this one's.
 */
class FlowEngineTest {

    private final TaskStore stateService = mock(TaskStore.class);
    private final AgentPresence sessions = mock(AgentPresence.class);

    private record FixedCapability(TaskAction action, Outcome outcome) implements TaskCapability {

        @Override
        public Outcome run(String taskId) {
            return outcome;
        }
    }

    private record NeverRunCapability(TaskAction action) implements TaskCapability {

        @Override
        public Outcome run(String taskId) {
            throw new AssertionError("the rules refused, so " + action.id() + " must never have run");
        }
    }

    @Test
    void saysTheTaskIsGoneRatherThanFailingObscurelyWhenAnotherTabClosedIt() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine(new NeverRunCapability(TaskAction.FOCUS))
                .run("ABC-1", TaskAction.FOCUS))
                .asInstanceOf(type(Refusal.class))
                .satisfies(refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.NO_SUCH_TASK))
                .satisfies(refusal -> assertThat(refusal).hasMessageContaining("No task ABC-1")
                        .hasMessageContaining("closed since this page loaded"));
    }

    @Test
    void refusesAnActionTheStatusDoesNotAllowWithoutLettingTheWorkStart() {
        havingTask("ABC-1", TaskStatus.IN_PROGRESS, false);

        assertThatThrownBy(() -> engine(new NeverRunCapability(TaskAction.DEPLOY))
                .run("ABC-1", TaskAction.DEPLOY))
                .asInstanceOf(type(Refusal.class))
                .satisfies(refusal -> assertThat(refusal.code())
                        .isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE))
                .satisfies(refusal -> assertThat(refusal)
                        .hasMessageContaining("Deploy is not available for ABC-1")
                        .hasMessageContaining("IN_PROGRESS"));
    }

    @Test
    void movesTheTaskToTheStatusTheRulesGiveForAnOutcomeThatWorked() {
        havingTask("ABC-1", TaskStatus.REVIEW_PENDING, false);
        FlowEngine engine = engine(new FixedCapability(TaskAction.SHIP,
                Outcome.ok("ship ABC-1: request opened", "review request: http://host/1")));

        assertThat(engine.run("ABC-1", TaskAction.SHIP)).isEqualTo("ship ABC-1: request opened");
        assertThat(statusWritten("ABC-1", TaskStatus.REVIEW_PENDING)).isEqualTo(TaskStatus.CI_POLLING);
    }

    @Test
    void writesNothingAtAllForWorkThatOnlyLooksAtTheTask() {
        havingTask("ABC-1", TaskStatus.IN_PROGRESS, false);
        FlowEngine engine = engine(new FixedCapability(TaskAction.FOCUS, Outcome.ok("focused ABC-1")));

        assertThat(engine.run("ABC-1", TaskAction.FOCUS)).isEqualTo("focused ABC-1");
        verify(stateService, never()).updateTask(any(), any());
    }

    /** A half-written shared branch must be RECORDED, not merely complained about. */
    @Test
    void stampsWhatALandingLeftBehindBeforeItRefusesTheHalfDoneRevert() {
        havingTask("ABC-1", TaskStatus.DEPLOYED, true);
        RuntimeException cause = new RuntimeException("push rejected");
        FlowEngine engine = engine(new FixedCapability(TaskAction.REVERT,
                Outcome.partial("reverted widget-api, storefront still live", "revert stopped", cause)));

        assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.REVERT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("reverted widget-api, storefront still live")
                .hasCause(cause);
        assertThat(statusWritten("ABC-1", TaskStatus.DEPLOYED)).isEqualTo(TaskStatus.DEPLOYED);
    }

    @Test
    void leavesTheTaskWhereItIsWhenAnOutcomeLeadsNowhereButStillHasSomethingToSay() {
        havingTask("ABC-1", TaskStatus.CI_POLLING, true);
        FlowEngine engine = engine(new FixedCapability(TaskAction.SWEEP,
                Outcome.ok("ABC-1: pipeline still running", "checks running")));

        assertThat(engine.run("ABC-1", TaskAction.SWEEP)).isEqualTo("ABC-1: pipeline still running");
        assertThat(statusWritten("ABC-1", TaskStatus.CI_POLLING)).isEqualTo(TaskStatus.CI_POLLING);
    }

    @Test
    void takesAnAliasEverywhereTheConsoleDoes() {
        when(stateService.canonicalTaskId("a1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build()));
        FlowEngine engine = engine(new FixedCapability(TaskAction.FOCUS, Outcome.ok("focused ABC-1")));

        assertThat(engine.run("a1", TaskAction.FOCUS)).isEqualTo("focused ABC-1");
    }

    private FlowEngine engine(TaskCapability capability) {
        return new FlowEngine(stateService, new Capabilities(List.of(capability)), sessions);
    }

    private void havingTask(String taskId, TaskStatus status, boolean hasReviewRequest) {
        when(stateService.canonicalTaskId(taskId)).thenReturn(taskId);
        when(stateService.task(taskId)).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", status).alias("a1")
                        .mrUrl(hasReviewRequest ? "http://host/1" : null).build()));
    }

    private TaskStatus statusWritten(String taskId, TaskStatus was) {
        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();
        verify(stateService).updateTask(eq(taskId), write.capture());
        return write.getValue().apply(TaskState.builder("proj", "/wt", was).build()).status();
    }
}
