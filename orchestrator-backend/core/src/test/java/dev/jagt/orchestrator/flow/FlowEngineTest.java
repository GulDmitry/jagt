package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
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
import static org.mockito.Mockito.times;
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

    private record KnowingCapability(TaskAction action, Outcome outcome, String known) implements TaskCapability {

        @Override
        public Outcome run(String taskId) {
            return outcome;
        }

        @Override
        public void vet(String project) {
            if (!known.equals(project)) {
                throw Refusal.byState("Unknown project '" + project + "'");
            }
        }

        @Override
        public Facts naming(Facts facts) {
            return facts.withWorkspace(facts.workspace().withRepoRequested(true));
        }

        @Override
        public Outcome run(String taskId, String project) {
            return Outcome.relayed("added " + project, outcome.stamp());
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
                new Capabilities(List.of(new NeverRunCapability(TaskAction.FOCUS)), List.of()), sessions, Clock.systemUTC(), ignored -> false);

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
                new Capabilities(List.of(new NeverRunCapability(TaskAction.DEPLOY)), List.of()), sessions, Clock.systemUTC(), ignored -> false);

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
                Outcome.ok("ship ABC-1: request opened", "review request: http://host/1"))), List.of()), sessions, Clock.systemUTC(), ignored -> false);
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
                new FixedCapability(TaskAction.FOCUS, Outcome.ok("focused ABC-1"))), List.of()), sessions, Clock.systemUTC(), ignored -> false);

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
                Outcome.partial("reverted widget-api, storefront still live", "revert stopped", cause))), List.of()), sessions, Clock.systemUTC(), ignored -> false);
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
                .mrUrl("http://host/1")
                .deployment(new Deployment(1, List.of(Deployment.Target.of("proj", "dev").withMerge("cafebabe1234")), 1, null)).build()));
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(
                new FixedCapability(TaskAction.REVERT, Outcome.ok("reverted ABC-1"))), List.of()), sessions, Clock.systemUTC(), ignored -> false);
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
                Outcome.ok("ABC-1: pipeline still running", "checks running"))), List.of()), sessions, Clock.systemUTC(), ignored -> false);
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
                new FixedCapability(TaskAction.FOCUS, Outcome.ok("focused ABC-1"))), List.of()), sessions, Clock.systemUTC(), ignored -> false);

        assertThat(engine.run("a1", TaskAction.FOCUS)).isEqualTo("focused ABC-1");
    }

    @Test
    void failsLoudlyWhenAnActionTheRulesAllowHasNoCapabilityDeclared() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build()));
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(), List.of()), sessions, Clock.systemUTC(), ignored -> false);

        assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.FOCUS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No capability declared for `focus`");
    }

    @Test
    void keepsTheTaskMessageWhenTheOutcomeMovedItWithoutStampingOne() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build()));
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(new FixedCapability(TaskAction.SHIP,
                Outcome.ok("ship ABC-1: request opened", null))), List.of()), sessions, Clock.systemUTC(), ignored -> false);
        ArgumentCaptor<UnaryOperator<TaskState>> write = ArgumentCaptor.captor();

        engine.run("ABC-1", TaskAction.SHIP);

        verify(stateService).updateTask(eq("ABC-1"), write.capture());
        TaskState before = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).message("keep me").build();
        assertThat(write.getValue().apply(before).message()).isEqualTo("keep me");
    }

    @Test
    void shipsAStuckTaskAgainOnlyWhenItsAgentIsGoneAsTheProbeAnswersAtTheMomentOfTheRun() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build()));
        when(sessions.agentLive("ABC-1")).thenReturn(true);
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(new NeverRunCapability(
                TaskAction.SHIP)), List.of()), sessions, Clock.systemUTC(), ignored -> false);

        assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.SHIP))
                .asInstanceOf(type(Refusal.class))
                .satisfies(refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
    }

    @Test
    void shipsAStuckTaskAgainWhenTheProbeSaysItsAgentIsGone() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build()));
        when(sessions.agentLive("ABC-1")).thenReturn(false);
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(new FixedCapability(
                TaskAction.SHIP, Outcome.ok("shipped again"))), List.of()), sessions, Clock.systemUTC(), ignored -> false);

        assertThat(engine.run("ABC-1", TaskAction.SHIP)).isEqualTo("shipped again");
    }

    @Test
    void refusesToShipAStuckTaskWhoseSessionWindowCouldNotBeRead() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build()));
        when(sessions.agentLive("ABC-1")).thenThrow(new AgentPresence.Unread("tmux timed out"));
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(new NeverRunCapability(
                TaskAction.SHIP)), List.of()), sessions, Clock.systemUTC(), ignored -> false);

        assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.SHIP))
                .asInstanceOf(type(Refusal.class))
                .satisfies(refusal -> assertThat(refusal.getMessage()).contains("window was not read"));
    }

    @Test
    void handsTheProjectAHumanNamesToTheCapabilityAndLandsWhereTheRulesLeadIt() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("alpha", "/wt", TaskStatus.REVIEW_PENDING).build()));
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(new KnowingCapability(
                TaskAction.ADD_REPO, Outcome.relayed("added", "repository added"), "beta")), List.of()),
                sessions, Clock.systemUTC(), ignored -> false);
        ArgumentCaptor<UnaryOperator<TaskState>> writes = ArgumentCaptor.captor();

        String said = engine.run("ABC-1", TaskAction.ADD_REPO, "beta");

        assertThat(said).isEqualTo("added beta");
        verify(stateService).updateTask(eq("ABC-1"), writes.capture());
        assertThat(writes.getValue().apply(TaskState.builder("alpha", "/wt", TaskStatus.REVIEW_PENDING).build())
                .status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"PLAN_PENDING", "DEPLOYED", "DEPLOY_CONFLICT", "REVERTED", "DONE"})
    void refusesANamedRepositoryAtAStatusThatCannotTakeOneAndWritesNothing(TaskStatus status) {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(TaskState.builder("alpha", "/wt", status).build()));
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(new KnowingCapability(
                TaskAction.ADD_REPO, Outcome.relayed("added", "repository added"), "beta")), List.of()),
                sessions, Clock.systemUTC(), ignored -> false);

        assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.ADD_REPO, "beta"))
                .asInstanceOf(type(Refusal.class))
                .satisfies(refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
        verify(stateService, never()).updateTask(any(), any());
    }

    @Test
    void refusesANamedRepositoryTheCapabilityDoesNotKnowAndWritesNothing() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("alpha", "/wt", TaskStatus.IN_PROGRESS).build()));
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(new KnowingCapability(
                TaskAction.ADD_REPO, Outcome.relayed("added", "repository added"), "beta")), List.of()),
                sessions, Clock.systemUTC(), ignored -> false);

        assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.ADD_REPO, "gamma"))
                .asInstanceOf(type(Refusal.class))
                .satisfies(refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));
        verify(stateService, never()).updateTask(any(), any());
    }

    @Test
    void refusesAProjectNamedForAVerbThatTakesNone() {
        when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
        when(stateService.task("ABC-1")).thenReturn(Optional.of(
                TaskState.builder("alpha", "/wt", TaskStatus.IN_PROGRESS).build()));
        FlowEngine engine = new FlowEngine(stateService, new Capabilities(List.of(
                new FixedCapability(TaskAction.FOCUS, Outcome.ok("focused ABC-1"))), List.of()),
                sessions, Clock.systemUTC(), ignored -> false);

        assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.FOCUS, "beta"))
                .asInstanceOf(type(Refusal.class))
                .satisfies(refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));
        verify(stateService, never()).updateTask(any(), any());
    }
}
