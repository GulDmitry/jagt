package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;
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
        FlowEngine engine = new FlowEngine(stateService,
                new Capabilities(List.of(new NeverRunCapability(TaskAction.FOCUS))), sessions);

        assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.FOCUS))
                .asInstanceOf(type(Refusal.class))
                .satisfies(refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.NO_SUCH_TASK))
                .satisfies(refusal -> assertThat(refusal).hasMessageContaining("No task ABC-1")
                        .hasMessageContaining("may have been closed"));
    }

    @Test
    void refusesAnActionTheStatusDoesNotAllowWithoutLettingTheWorkStart() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()));
        FlowEngine engine = new FlowEngine(stateService,
                new Capabilities(List.of(new NeverRunCapability(TaskAction.DEPLOY))), sessions);

        assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.DEPLOY))
                .asInstanceOf(type(Refusal.class))
                .satisfies(refusal -> assertThat(refusal.code())
                        .isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE))
                .satisfies(refusal -> assertThat(refusal)
                        .hasMessageContaining("Deploy is not available for ABC-1")
                        .hasMessageContaining("IN_PROGRESS"));
    }

    @Test
    void movesTheTaskToTheStatusTheRulesGiveForAnOutcomeThatWorked() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build()));
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(new FixedCapability(TaskAction.SHIP,
                Outcome.ok("ship ABC-1: request opened", "review request: http://host/1")))), sessions);
        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();

        String said = engine.run("ABC-1", TaskAction.SHIP);

        assertThat(said).isEqualTo("ship ABC-1: request opened");
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        assertThat(write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build())
                .status()).isEqualTo(TaskStatus.CI_POLLING);
    }

    @Test
    void writesNothingAtAllForWorkThatOnlyLooksAtTheTask() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()));
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(
                new FixedCapability(TaskAction.FOCUS, Outcome.ok("focused ABC-1")))), sessions);

        assertThat(engine.run("ABC-1", TaskAction.FOCUS)).isEqualTo("focused ABC-1");
        verify(stateService, never()).updateTask(any(), any());
    }

    @Test
    void stampsWhatALandingLeftBehindBeforeItRefusesTheHalfDoneRevert() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).mrUrl("http://host/1").build()));
        RuntimeException cause = new RuntimeException("push rejected");
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(new FixedCapability(TaskAction.REVERT,
                Outcome.partial("reverted widget-api, storefront still live", "revert stopped", cause)))), sessions);
        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();

        assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.REVERT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("reverted widget-api, storefront still live")
                .hasCause(cause);
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        assertThat(write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build())
                .status()).isEqualTo(TaskStatus.DEPLOYED);
    }

    @Test
    void revertsWhatADeployThatBrokeOffLeftLiveWhileTheStatusStayedPut() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
                .mrUrl("http://host/1").deployCommit("cafebabe1234").build()));
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(
                new FixedCapability(TaskAction.REVERT, Outcome.ok("reverted ABC-1")))), sessions);
        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();

        String said = engine.run("ABC-1", TaskAction.REVERT);

        assertThat(said).isEqualTo("reverted ABC-1");
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        assertThat(write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.APPROVED).build())
                .status()).isEqualTo(TaskStatus.REVERTED);
    }

    @Test
    void leavesTheTaskWhereItIsWhenAnOutcomeLeadsNowhereButStillHasSomethingToSay() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).mrUrl("http://host/1").build()));
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(new FixedCapability(TaskAction.SWEEP,
                Outcome.ok("ABC-1: pipeline still running", "checks running")))), sessions);
        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();

        String said = engine.run("ABC-1", TaskAction.SWEEP);

        assertThat(said).isEqualTo("ABC-1: pipeline still running");
        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        assertThat(write.getValue().apply(TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build())
                .status()).isEqualTo(TaskStatus.CI_POLLING);
    }

    @Test
    void takesAnAliasEverywhereTheConsoleDoes() {
        when(stateService.canonicalTaskId("a1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build()));
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(
                new FixedCapability(TaskAction.FOCUS, Outcome.ok("focused ABC-1")))), sessions);

        assertThat(engine.run("a1", TaskAction.FOCUS)).isEqualTo("focused ABC-1");
    }
}
