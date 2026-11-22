package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.capability.Capabilities;
import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class FlowEngineTest {

  private static final Clock CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

  private final FlowRules rules = new FlowRules(new TaskChart(Composition.builtIn()));

  private final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final ReducingStore stateService = mock(ReducingStore.class);
  private final AgentPresence sessions = mock(AgentPresence.class);
  private final FactSources facts =
      new FactSources(
          sessions,
          ignored -> false,
          id -> Optional.empty(),
          id -> false,
          () -> ActionOrigin.BOARD);
  private final TaskState shipping = TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build();
  private final Capabilities neverRunShip =
      new Capabilities(List.of(new NeverRunCapability(TaskAction.SHIP)), List.of());
  private final TaskState reviewPending =
      TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

  private final FlowEngine lineEngine =
      new FlowEngine(
          stateService,
          new Capabilities(List.of(new LineCapability()), List.of()),
          facts,
          CLOCK,
          rules);

  private static final class FixedCapability implements TaskCapability {
    private final TaskAction action;
    private final Outcome outcome;

    FixedCapability(TaskAction action, Outcome outcome) {
      this.action = action;
      this.outcome = outcome;
    }

    @Override
    public TaskAction action() {
      return action;
    }

    @Override
    public Outcome run(String taskId) {
      return outcome;
    }
  }

  private static final class KnowingCapability implements TaskCapability {
    private final TaskAction action;
    private final Outcome outcome;
    private final String known;

    KnowingCapability(TaskAction action, Outcome outcome, String known) {
      this.action = action;
      this.outcome = outcome;
      this.known = known;
    }

    @Override
    public TaskAction action() {
      return action;
    }

    @Override
    public Outcome run(String taskId) {
      return outcome;
    }

    @Override
    public Outcome run(String taskId, String project) {
      return Outcome.relayed("added " + project, outcome.stamp());
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
  }

  private static final class ScenariosThatAre implements TaskCapability {
    private final boolean ready;
    private final Outcome outcome;

    ScenariosThatAre(boolean ready, Outcome outcome) {
      this.ready = ready;
      this.outcome = outcome;
    }

    @Override
    public TaskAction action() {
      return TaskAction.SCENARIOS;
    }

    @Override
    public boolean offered() {
      return ready;
    }

    @Override
    public Outcome run(String taskId) {
      return outcome;
    }
  }

  private record LineCapability() implements TaskCapability {

    @Override
    public TaskAction action() {
      return TaskAction.RUN;
    }

    @Override
    public Outcome run(String taskId) {
      throw new AssertionError("run needs a line");
    }

    @Override
    public Outcome run(String taskId, String project) {
      throw new AssertionError("a project is never a line");
    }

    @Override
    public void vet(String project) {}

    @Override
    public void vetLine(String line) {
      if (line.isBlank()) {
        throw Refusal.byState("run takes one line of text");
      }
    }

    @Override
    public Outcome runLine(String taskId, String line) {
      return Outcome.relayed("ran " + line, null);
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
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(List.of(new NeverRunCapability(TaskAction.FOCUS)), List.of()),
            facts,
            CLOCK,
            rules);

    assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.FOCUS))
        .asInstanceOf(type(Refusal.class))
        .satisfies(refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.NO_SUCH_TASK))
        .satisfies(refusal ->
            assertThat(refusal)
                .hasMessageContaining("No task ABC-1")
                .hasMessageContaining("may have been closed"));
  }

  @Test
  void handsTheTypedLineToTheVerbThatTakesOne() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()));
    assertThat(lineEngine.runLine("ABC-1", TaskAction.RUN, "ls -la")).isEqualTo("ran ls -la");
  }

  @Test
  void refusesLineTheVerbVetsAwayBeforeAnythingRuns() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()));

    assertThatThrownBy(() -> lineEngine.runLine("ABC-1", TaskAction.RUN, " "))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("one line of text");
  }

  @Test
  void refusesProjectNamedForVerbThatTakesLine() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()));
    assertThatThrownBy(() -> lineEngine.run("ABC-1", TaskAction.RUN, "ls -la"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("run takes no project");
  }

  @Test
  void refusesLineTypedForVerbThatTakesNone() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1")).thenReturn(Optional.of(reviewPending));
    FlowEngine engine = new FlowEngine(stateService, neverRunShip, facts, CLOCK, rules);

    assertThatThrownBy(() -> engine.runLine("ABC-1", TaskAction.SHIP, "ls -la"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("ship takes no line");
  }

  @Test
  void refusesScenariosWhereAcceptanceIsNotReady() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(List.of(new ScenariosThatAre(false, Outcome.ok("opened"))), List.of()),
            facts,
            CLOCK,
            rules);

    assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.SCENARIOS))
        .asInstanceOf(type(Refusal.class))
        .satisfies(
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
  }

  @Test
  void takesScenariosWhereAcceptanceIsReady() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(List.of(new ScenariosThatAre(true, Outcome.ok("opened"))), List.of()),
            facts,
            CLOCK,
            rules);

    assertThat(engine.run("ABC-1", TaskAction.SCENARIOS)).isEqualTo("opened");
  }

  @Test
  void refusesAnActionTheStatusDoesNotAllowWithoutLettingTheWorkStart() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(List.of(new NeverRunCapability(TaskAction.DEPLOY)), List.of()),
            facts,
            CLOCK,
            rules);

    assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.DEPLOY))
        .asInstanceOf(type(Refusal.class))
        .satisfies(
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE))
        .satisfies(refusal ->
            assertThat(refusal)
                .hasMessageContaining("Deploy is not available for ABC-1")
                .hasMessageContaining("IN_PROGRESS"));
  }

  @Test
  void movesTheTaskToTheStatusTheRulesGiveForAnOutcomeThatWorked() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1")).thenReturn(Optional.of(reviewPending));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(
                List.of(
                    new FixedCapability(
                        TaskAction.SHIP,
                        Outcome.ok("ship ABC-1: request opened", "review request: http://host/1"))),
                List.of()),
            facts,
            CLOCK,
            rules);
    ArgumentCaptor<BiFunction<TaskReducer, TaskState, TaskState>> write = ArgumentCaptor.captor();

    String said = engine.run("ABC-1", TaskAction.SHIP);

    assertThat(said).isEqualTo("ship ABC-1: request opened");
    verify(stateService).reduce(eq("ABC-1"), write.capture());
    assertThat(
            write
                .getValue()
                .apply(reducer, TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build())
                .standing()
                .status())
        .isEqualTo(TaskStatus.CI_POLLING);
  }

  @Test
  void leavesTheReportTheAgentMadeWhileTheRelayedShipWasStillRunning() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1")).thenReturn(Optional.of(reviewPending));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(
                List.of(
                    new FixedCapability(
                        TaskAction.SHIP, Outcome.relayed("ship ABC-1: relayed", "shipping"))),
                List.of()),
            facts,
            CLOCK,
            rules);
    ArgumentCaptor<BiFunction<TaskReducer, TaskState, TaskState>> write = ArgumentCaptor.captor();

    engine.run("ABC-1", TaskAction.SHIP);

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    TaskState reported =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).message("request 1 open").build();
    TaskState written = write.getValue().apply(reducer, reported);
    assertThat(written.standing().status()).isEqualTo(TaskStatus.CI_POLLING);
    assertThat(written.standing().message()).isEqualTo("request 1 open");
  }

  @Test
  void writesNothingAtAllForWorkThatOnlyLooksAtTheTask() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build()));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(
                List.of(new FixedCapability(TaskAction.FOCUS, Outcome.ok("focused ABC-1"))),
                List.of()),
            facts,
            CLOCK,
            rules);

    assertThat(engine.run("ABC-1", TaskAction.FOCUS)).isEqualTo("focused ABC-1");
    verify(stateService, never()).reduce(any(), any());
  }

  @Test
  void stampsWhatLandingLeftBehindBeforeItRefusesTheHalfDoneRevert() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED)
                    .mrUrl("http://host/1")
                    .build()));
    RuntimeException cause = new RuntimeException("push rejected");
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(
                List.of(
                    new FixedCapability(
                        TaskAction.REVERT,
                        Outcome.partial(
                            "reverted widget-api, storefront still live",
                            "revert stopped",
                            cause))),
                List.of()),
            facts,
            CLOCK,
            rules);
    ArgumentCaptor<BiFunction<TaskReducer, TaskState, TaskState>> write = ArgumentCaptor.captor();

    assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.REVERT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("reverted widget-api, storefront still live")
        .hasCause(cause);
    verify(stateService).reduce(eq("ABC-1"), write.capture());
    assertThat(
            write
                .getValue()
                .apply(reducer, TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build())
                .standing()
                .status())
        .isEqualTo(TaskStatus.DEPLOYED);
  }

  @Test
  void revertsWhatDeployThatBrokeOffLeftLiveWhileTheStatusStayedPut() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
                    .mrUrl("http://host/1")
                    .deployment(
                        new Deployment(
                            1,
                            List.of(Deployment.Target.of("proj", "dev").withMerge("cafebabe1234")),
                            1,
                            null))
                    .build()));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(
                List.of(new FixedCapability(TaskAction.REVERT, Outcome.ok("reverted ABC-1"))),
                List.of()),
            facts,
            CLOCK,
            rules);
    ArgumentCaptor<BiFunction<TaskReducer, TaskState, TaskState>> write = ArgumentCaptor.captor();

    String said = engine.run("ABC-1", TaskAction.REVERT);

    assertThat(said).isEqualTo("reverted ABC-1");
    verify(stateService).reduce(eq("ABC-1"), write.capture());
    assertThat(
            write
                .getValue()
                .apply(reducer, TaskState.builder("proj", "/wt", TaskStatus.APPROVED).build())
                .standing()
                .status())
        .isEqualTo(TaskStatus.REVERTED);
  }

  @Test
  void leavesTheTaskWhereItIsWhenAnOutcomeLeadsNowhereButStillHasSomethingToSay() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                    .mrUrl("http://host/1")
                    .build()));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(
                List.of(
                    new FixedCapability(
                        TaskAction.SWEEP,
                        Outcome.ok("ABC-1: pipeline still running", "checks running"))),
                List.of()),
            facts,
            CLOCK,
            rules);
    ArgumentCaptor<BiFunction<TaskReducer, TaskState, TaskState>> write = ArgumentCaptor.captor();

    String said = engine.run("ABC-1", TaskAction.SWEEP);

    assertThat(said).isEqualTo("ABC-1: pipeline still running");
    verify(stateService).reduce(eq("ABC-1"), write.capture());
    assertThat(
            write
                .getValue()
                .apply(reducer, TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build())
                .standing()
                .status())
        .isEqualTo(TaskStatus.CI_POLLING);
  }

  @Test
  void takesAnAliasEverywhereTheConsoleDoes() {
    when(stateService.canonicalTaskId("a1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build()));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(
                List.of(new FixedCapability(TaskAction.FOCUS, Outcome.ok("focused ABC-1"))),
                List.of()),
            facts,
            CLOCK,
            rules);

    assertThat(engine.run("a1", TaskAction.FOCUS)).isEqualTo("focused ABC-1");
  }

  @Test
  void failsLoudlyWhenAnActionTheRulesAllowHasNoCapabilityDeclared() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1")).thenReturn(Optional.of(reviewPending));
    FlowEngine engine =
        new FlowEngine(stateService, new Capabilities(List.of(), List.of()), facts, CLOCK, rules);

    assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.FOCUS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No capability declared for `focus`");
  }

  @Test
  void keepsTheTaskMessageWhenTheOutcomeMovedItWithoutStampingOne() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1")).thenReturn(Optional.of(reviewPending));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(
                List.of(
                    new FixedCapability(
                        TaskAction.SHIP, Outcome.ok("ship ABC-1: request opened", null))),
                List.of()),
            facts,
            CLOCK,
            rules);
    ArgumentCaptor<BiFunction<TaskReducer, TaskState, TaskState>> write = ArgumentCaptor.captor();

    engine.run("ABC-1", TaskAction.SHIP);

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    TaskState before =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).message("keep me").build();
    assertThat(write.getValue().apply(reducer, before).standing().message()).isEqualTo("keep me");
  }

  @Test
  void shipsStuckTaskAgainOnlyWhenItsAgentIsGoneAsTheProbeAnswersAtTheMomentOfTheRun() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1")).thenReturn(Optional.of(shipping));
    when(sessions.agentLive("ABC-1")).thenReturn(true);
    FlowEngine engine = new FlowEngine(stateService, neverRunShip, facts, CLOCK, rules);

    assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.SHIP))
        .asInstanceOf(type(Refusal.class))
        .satisfies(
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
  }

  @Test
  void shipsStuckTaskAgainWhenTheProbeSaysItsAgentIsGone() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1")).thenReturn(Optional.of(shipping));
    when(sessions.agentLive("ABC-1")).thenReturn(false);
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(
                List.of(new FixedCapability(TaskAction.SHIP, Outcome.ok("shipped again"))),
                List.of()),
            facts,
            CLOCK,
            rules);

    assertThat(engine.run("ABC-1", TaskAction.SHIP)).isEqualTo("shipped again");
  }

  @Test
  void refusesToShipStuckTaskWhoseSessionWindowCouldNotBeRead() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1")).thenReturn(Optional.of(shipping));
    when(sessions.agentLive("ABC-1")).thenThrow(new AgentPresence.Unread("tmux timed out"));
    FlowEngine engine = new FlowEngine(stateService, neverRunShip, facts, CLOCK, rules);

    assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.SHIP))
        .asInstanceOf(type(Refusal.class))
        .satisfies(refusal -> assertThat(refusal.getMessage()).contains("window was not read"));
  }

  @Test
  void handsTheProjectHumanNamesToTheCapabilityAndLandsWhereTheRulesLeadIt() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(TaskState.builder("alpha", "/wt", TaskStatus.REVIEW_PENDING).build()));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(
                List.of(
                    new KnowingCapability(
                        TaskAction.ADD_REPO, Outcome.relayed("added", "repository added"), "beta")),
                List.of()),
            facts,
            CLOCK,
            rules);
    ArgumentCaptor<BiFunction<TaskReducer, TaskState, TaskState>> writes = ArgumentCaptor.captor();

    String said = engine.run("ABC-1", TaskAction.ADD_REPO, "beta");

    assertThat(said).isEqualTo("added beta");
    verify(stateService).reduce(eq("ABC-1"), writes.capture());
    assertThat(
            writes
                .getValue()
                .apply(
                    reducer, TaskState.builder("alpha", "/wt", TaskStatus.REVIEW_PENDING).build())
                .standing()
                .status())
        .isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"PLAN_PENDING", "DEPLOYED", "DEPLOY_CONFLICT", "REVERTED", "DONE"})
  void refusesNamedRepositoryAtStatusThatCannotTakeOneAndWritesNothing(TaskStatus status) {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(TaskState.builder("alpha", "/wt", status).build()));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(
                List.of(
                    new KnowingCapability(
                        TaskAction.ADD_REPO, Outcome.relayed("added", "repository added"), "beta")),
                List.of()),
            facts,
            CLOCK,
            rules);

    assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.ADD_REPO, "beta"))
        .asInstanceOf(type(Refusal.class))
        .satisfies(
            refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.ACTION_NOT_AVAILABLE));
    verify(stateService, never()).reduce(any(), any());
  }

  @Test
  void refusesNamedRepositoryTheCapabilityDoesNotKnowAndWritesNothing() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(TaskState.builder("alpha", "/wt", TaskStatus.IN_PROGRESS).build()));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(
                List.of(
                    new KnowingCapability(
                        TaskAction.ADD_REPO, Outcome.relayed("added", "repository added"), "beta")),
                List.of()),
            facts,
            CLOCK,
            rules);

    assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.ADD_REPO, "gamma"))
        .asInstanceOf(type(Refusal.class))
        .satisfies(refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));
    verify(stateService, never()).reduce(any(), any());
  }

  @Test
  void refusesProjectNamedForVerbThatTakesNone() {
    when(stateService.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(TaskState.builder("alpha", "/wt", TaskStatus.IN_PROGRESS).build()));
    FlowEngine engine =
        new FlowEngine(
            stateService,
            new Capabilities(
                List.of(new FixedCapability(TaskAction.FOCUS, Outcome.ok("focused ABC-1"))),
                List.of()),
            facts,
            CLOCK,
            rules);

    assertThatThrownBy(() -> engine.run("ABC-1", TaskAction.FOCUS, "beta"))
        .asInstanceOf(type(Refusal.class))
        .satisfies(refusal -> assertThat(refusal.code()).isEqualTo(Refusal.Code.STATE));
    verify(stateService, never()).reduce(any(), any());
  }
}
