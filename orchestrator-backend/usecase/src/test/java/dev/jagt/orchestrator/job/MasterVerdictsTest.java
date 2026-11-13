package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestMasterClaimsBuilder.masterClaims;
import static dev.jagt.orchestrator.job.TestRolesRootBuilder.rolesRoot;
import static dev.jagt.orchestrator.job.TestStepRelayBuilder.stepRelay;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskMovedOn;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class MasterVerdictsTest {

  @TempDir Path root;

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final MasterShip ship = mock(MasterShip.class);
  private final MasterDecisions decisions = spy(new MasterDecisions(mock(WorktreeChanges.class)));
  private final ConfigService.ConfigFile.MasterConfig acting =
      new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
  private final ConfigService.ConfigFile.MasterConfig judging =
      new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null);
  private final MasterReview.Verdict guardInvertedBeforeAnyRound =
      new MasterReview.Verdict(
          MasterReview.Kind.NOT_READY,
          List.of(
              new MasterReview.Said(
                  "developer",
                  new Finding(
                      "Foo.java", "the guard is inverted", "", "blocking", "", "behaviour"))),
          List.of(),
          List.of(),
          0,
          "");
  private final MasterReview.Verdict guardInverted =
      new MasterReview.Verdict(
          MasterReview.Kind.NOT_READY,
          List.of(
              new MasterReview.Said(
                  "developer",
                  new Finding(
                      "Foo.java", "the guard is inverted", "", "blocking", "", "behaviour"))),
          List.of(),
          List.of(),
          1,
          "");
  private final MasterReview.Verdict cartGuardInverted =
      new MasterReview.Verdict(
          MasterReview.Kind.NOT_READY,
          List.of(
              new MasterReview.Said(
                  "developer",
                  new Finding(
                      "Cart.java", "the guard is inverted", "", "blocking", "", "behaviour"))),
          List.of(),
          List.of(),
          1,
          "");
  private final MasterReview.Verdict orderGuardInverted =
      new MasterReview.Verdict(
          MasterReview.Kind.NOT_READY,
          List.of(
              new MasterReview.Said(
                  "developer",
                  new Finding(
                      "Order.java", "the guard is inverted", "", "blocking", "", "behaviour"))),
          List.of(),
          List.of(),
          2,
          "");
  private final MasterReview.Verdict paymentGuardInverted =
      new MasterReview.Verdict(
          MasterReview.Kind.NOT_READY,
          List.of(
              new MasterReview.Said(
                  "developer",
                  new Finding(
                      "Payment.java", "the guard is inverted", "", "blocking", "", "behaviour"))),
          List.of(),
          List.of(),
          3,
          "");
  private final MasterReview.Verdict invoiceGuardInverted =
      new MasterReview.Verdict(
          MasterReview.Kind.NOT_READY,
          List.of(
              new MasterReview.Said(
                  "developer",
                  new Finding(
                      "Invoice.java", "the guard is inverted", "", "blocking", "", "behaviour"))),
          List.of(),
          List.of(),
          4,
          "");
  private final MasterReview.Verdict delayNeverPinned =
      new MasterReview.Verdict(
          MasterReview.Kind.NOT_READY,
          List.of(
              new MasterReview.Said(
                  "acceptance",
                  new Finding(
                      "Foo.java", "the delay is never pinned", "", "blocking", "delay", "test"))),
          List.of(),
          List.of(),
          1,
          "");
  private final MasterReview.Verdict delayStillNeverPinned =
      new MasterReview.Verdict(
          MasterReview.Kind.NOT_READY,
          List.of(
              new MasterReview.Said(
                  "acceptance",
                  new Finding(
                      "Foo.java",
                      "the delay is still never pinned",
                      "",
                      "blocking",
                      "delay",
                      "test"))),
          List.of(),
          List.of(),
          2,
          "");
  private final MasterReview.Verdict noTestPinsDelay =
      new MasterReview.Verdict(
          MasterReview.Kind.NOT_READY,
          List.of(
              new MasterReview.Said(
                  "acceptance",
                  new Finding(
                      "Foo.java", "no test pins the delay", "", "blocking", "delay", "test"))),
          List.of(),
          List.of(),
          3,
          "");
  private final MasterReview.Verdict delayNeverPinnedAgain =
      new MasterReview.Verdict(
          MasterReview.Kind.NOT_READY,
          List.of(
              new MasterReview.Said(
                  "acceptance",
                  new Finding(
                      "Foo.java", "the delay is never pinned", "", "blocking", "delay", "test"))),
          List.of(),
          List.of(),
          4,
          "");
  private final MasterReview.Verdict ready =
      new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, "");
  private final MasterReview.Said unguardedDelay =
      new MasterReview.Said(
          "acceptance",
          new Finding(
              "Foo.java",
              "the delay is never pinned",
              "unguarded delay",
              "blocking",
              "delay",
              "test"));
  private final MasterReview.Verdict unguardedDelayOnRound7 =
      new MasterReview.Verdict(
          MasterReview.Kind.NOT_READY, List.of(unguardedDelay), List.of(), List.of(), 7, "");
  private TaskState reviewPending;
  private MasterClaims claims;
  private StateService state;
  private StepRelay relay;
  private MasterVerdicts verdicts;

  @BeforeEach
  void setUp() throws Exception {
    state = stateService().withRoot(root).build();
    rolesRoot().withRoot(root).build();
    relay = stepRelay().withRoot(root).build(state, sessions, Clock.systemUTC());
    claims = masterClaims().build(state, Clock.systemUTC());
    reviewPending =
        TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).alias("a1").build();
    verdicts = new MasterVerdicts(relay, ship, claims, decisions);
  }

  @Test
  void sendsRoundThatIsNotReadyBackToWhoeverWroteTheCode() throws Exception {
    state.putTask("ABC-1", reviewPending);

    verdicts.act("ABC-1", reviewPending, guardInverted, acting);

    verify(sessions).writeTaskContext(eq("ABC-1"), contains("the guard is inverted"));
    verify(ship, never()).ship(anyString(), any());
  }

  @ParameterizedTest
  @EnumSource(
      value = MasterReview.Kind.class,
      names = {"NOT_READY", "QUESTION"})
  void dropsVerdictOfTaskTheHumanShippedWhileTheMasterRead(MasterReview.Kind kind)
      throws Exception {
    state.putTask("ABC-1", reviewPending);
    state.updateTask(
        "ABC-1",
        live ->
            live.withStatus(TaskStatus.SHIPPING, null, 6_000)
                .withTurn(new Turn("ship", 6, "d", 6_000, Map.of(), List.of())));
    MasterReview.Verdict verdict =
        new MasterReview.Verdict(
            kind,
            List.of(
                new MasterReview.Said(
                    "developer",
                    new Finding(
                        "Foo.java", "the guard is inverted", "", "blocking", "", "behaviour"))),
            List.of(),
            List.of("override the gate?"),
            0,
            "");

    assertThatThrownBy(() -> verdicts.act("ABC-1", reviewPending, verdict, acting))
        .isInstanceOf(TaskMovedOn.class);

    verify(sessions, never()).writeTaskContext(anyString(), anyString());
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.SHIPPING);
  }

  @Test
  void dropsTheAnswerToQuestionTheSessionMovedOnFromWhileTheMasterRead() throws Exception {
    state.putTask("ABC-1", reviewPending);
    state.updateTask("ABC-1", live -> live.withTurn(live.turnOrNone().closed(2_000)));

    assertThatThrownBy(
            () ->
                verdicts.answered(
                    "ABC-1",
                    reviewPending,
                    "keep v2?",
                    new MasterPanel.Decision("keep v2 beside v3", "")))
        .isInstanceOf(TaskMovedOn.class);

    verify(sessions, never()).writeTaskContext(anyString(), anyString());
    verify(decisions, never()).answered(any());
  }

  @Test
  void putsTheTaskBackAndStopsItWhenTheSessionCouldNotBeTold() throws Exception {
    state.putTask("ABC-1", reviewPending);
    when(sessions.writeTaskContext(eq("ABC-1"), anyString()))
        .thenThrow(new IllegalStateException("no window"));

    assertThatThrownBy(
            () -> verdicts.act("ABC-1", reviewPending, guardInvertedBeforeAnyRound, acting))
        .isInstanceOf(IllegalStateException.class);

    assertThat(state.task("ABC-1").orElseThrow())
        .returns(TaskStatus.REVIEW_PENDING, TaskState::status)
        .returns(
            TaskStop.Trigger.UNDELIVERED, live -> Objects.requireNonNull(live.stop()).trigger());
  }

  @Test
  void leavesTaskThatMovedOnWhenTheSessionCouldNotBeTold() throws Exception {
    state.putTask("ABC-1", reviewPending);
    when(sessions.writeTaskContext(eq("ABC-1"), anyString()))
        .thenAnswer(
            told -> {
              state.updateTask(
                  "ABC-1",
                  live ->
                      live.withStatus(TaskStatus.SHIPPING, null, 7_000)
                          .withTurn(new Turn("ship", 9, "d", 7_000, Map.of(), List.of())));
              throw new IllegalStateException("no window");
            });

    assertThatThrownBy(
            () -> verdicts.act("ABC-1", reviewPending, guardInvertedBeforeAnyRound, acting))
        .isInstanceOf(IllegalStateException.class);

    assertThat(state.task("ABC-1").orElseThrow())
        .returns(TaskStatus.SHIPPING, TaskState::status)
        .returns(false, TaskState::stopped);
  }

  @Test
  void countsNoRoundForVerdictDroppedBecauseTheTaskMovedOn() throws Exception {
    state.putTask("ABC-1", reviewPending);
    state.updateTask(
        "ABC-1",
        live ->
            live.withStatus(TaskStatus.SHIPPING, null, 6_000)
                .withTurn(new Turn("ship", 6, "d", 6_000, Map.of(), List.of())));

    assertThatThrownBy(
            () -> verdicts.act("ABC-1", reviewPending, guardInvertedBeforeAnyRound, acting))
        .isInstanceOf(TaskMovedOn.class);

    verify(decisions, never()).returned(any(), anyLong(), any());
  }

  @Test
  void writesNoStateWhileTheSessionIsBeingTold() throws Exception {
    state.putTask("ABC-1", reviewPending);
    state.putTask("ABC-2", reviewPending);
    when(sessions.writeTaskContext(eq("ABC-1"), anyString()))
        .thenAnswer(
            told ->
                CompletableFuture.supplyAsync(() -> state.updateTask("ABC-2", other -> other))
                        .get(2, TimeUnit.SECONDS)
                    ? "told"
                    : "not told");

    boolean moved = verdicts.act("ABC-1", reviewPending, guardInvertedBeforeAnyRound, acting);

    assertThat(moved).isTrue();
  }

  @Test
  void takesNotReadyRoundBackToWorkSoTheFixedOneIsReadAgain() throws Exception {
    state.putTask("ABC-1", reviewPending);

    boolean moved = verdicts.act("ABC-1", reviewPending, guardInverted, acting);

    assertThat(moved).isTrue();
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void signsTheMoveItsVerdictTookAsTheMasters() throws Exception {
    state.putTask("ABC-1", reviewPending);

    verdicts.act("ABC-1", reviewPending, guardInverted, acting);

    assertThat(state.task("ABC-1").orElseThrow().history().getLast().origin())
        .isEqualTo(ActionOrigin.MASTER);
  }

  @Test
  void recordsMissItsVerdictSetOffAsTheMasters() throws Exception {
    TaskState task =
        TaskState.builder("proj", root.toString(), TaskStatus.CI_POLLING).alias("a1").build();
    state.putTask("ABC-1", task);

    verdicts.act("ABC-1", task, guardInverted, acting);

    assertThat(Files.readString(root.resolve("misses.jsonl")))
        .contains("\"step\":\"round-not-ready\"", "\"by\":\"master\"");
  }

  @Test
  void putsTheSessionBackToWorkOnTheAnswerTheMasterGaveIt() throws Exception {
    state.putTask("ABC-1", reviewPending);

    boolean moved =
        verdicts.answered(
            "ABC-1", reviewPending, "keep v2?", new MasterPanel.Decision("keep v2 beside v3", ""));

    assertThat(moved).isTrue();
    assertThat(state.task("ABC-1").orElseThrow())
        .returns(TaskStatus.IN_PROGRESS, TaskState::status)
        .returns("master answered the question", TaskState::message);
  }

  @Test
  void tellsTheSessionTheInstructionOfStopTheMasterLiftedAndLeavesItAtWork() throws Exception {
    state.putTask("ABC-1", reviewPending);

    boolean told = verdicts.stopReturned("ABC-1", reviewPending, "fix the fixture, not the test");

    assertThat(told).isTrue();
    verify(sessions).writeTaskContext(eq("ABC-1"), contains("fix the fixture, not the test"));
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void keepsTheDeployConflictHandOffStandingUnderTheAnswerToQuestion() throws Exception {
    TaskState task =
        TaskState.builder("proj", root.toString(), TaskStatus.DEPLOY_CONFLICT).alias("a1").build();
    state.putTask("ABC-42", task);

    verdicts.answered(
        "ABC-42", task, "keep both imports?", new MasterPanel.Decision("keep both imports", ""));

    verify(sessions).relayUnder(eq("ABC-42"), contains("keep both imports"), eq(false));
    verify(sessions, never()).writeTaskContext(eq("ABC-42"), anyString());
  }

  @Test
  void keepsTheSessionOfStageCheckAnsweredAgainOverTheSameTree() throws Exception {
    state.putTask("ABC-1", reviewPending);

    verdicts.answered(
        "ABC-1",
        reviewPending,
        "override the gate?",
        new MasterPanel.Decision("refactor the shared block", ""));

    verify(sessions).writeTaskContext(eq("ABC-1"), contains("refactor the shared block"));
    verify(sessions, never()).relayFresh(anyString(), anyString());
  }

  @Test
  void settlesWhatNotReadyRoundSentBackSoTheNextRoundDoesNotReopenIt() throws Exception {
    state.putTask("ABC-1", reviewPending);

    verdicts.act("ABC-1", reviewPending, guardInverted, acting);

    verify(decisions)
        .record(reviewPending, "- [developer] Foo.java — the guard is inverted (behaviour)");
  }

  @Test
  void relaysFindingAnEarlierRoundMadeMarkedAsRepeat() throws Exception {
    state.putTask("ABC-1", reviewPending);
    decisions.returned(reviewPending, 3, List.of(unguardedDelay));

    verdicts.act("ABC-1", reviewPending, unguardedDelayOnRound7, acting);

    verify(sessions)
        .writeTaskContext(
            eq("ABC-1"),
            contains(
                "- [acceptance] Foo.java delay — the delay is never pinned (unguarded delay, test)"
                    + " [repeat]"));
  }

  @Test
  void returnsSecondRoundOfRepeatsToTheSessionInsteadOfAskingAnyone() throws Exception {
    state.putTask("ABC-1", reviewPending);
    decisions.returned(reviewPending, 3, List.of(unguardedDelay));
    decisions.returned(reviewPending, 5, List.of(unguardedDelay));

    verdicts.act("ABC-1", reviewPending, unguardedDelayOnRound7, acting);

    assertThat(state.task("ABC-1").orElseThrow())
        .returns(AgentReport.PLAIN, TaskState::report)
        .returns(TaskStatus.IN_PROGRESS, TaskState::status);
  }

  @Test
  void stopsTheTaskOnFourthRoundRunningThatOnlyRepeatsTheFirst() throws Exception {
    state.putTask("ABC-1", reviewPending);
    verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), delayNeverPinned, acting);
    state.updateTask(
        "ABC-1", handedBack -> handedBack.withStatus(TaskStatus.REVIEW_PENDING, "fixed", 1));
    verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), delayStillNeverPinned, acting);
    state.updateTask(
        "ABC-1", handedBack -> handedBack.withStatus(TaskStatus.REVIEW_PENDING, "fixed", 2));
    verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), noTestPinsDelay, acting);
    state.updateTask(
        "ABC-1", handedBack -> handedBack.withStatus(TaskStatus.REVIEW_PENDING, "fixed", 3));

    boolean moved =
        verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), delayNeverPinnedAgain, acting);

    assertThat(moved).isFalse();
    assertThat(state.task("ABC-1").orElseThrow())
        .extracting(TaskState::report, done -> Objects.requireNonNull(done.stop()).trigger())
        .containsExactly(AgentReport.PLAIN, TaskStop.Trigger.ROUNDS);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    "each session report was reworked,plan 1,plan 2,plan 3,plan 4,true,false",
    "the session reported the same each time,plan 1,plan 1,plan 1,plan 1,false,true"
  })
  void fourthReturnOfTheSameFindingsDependsOnWhatTheSessionReported(
      String situation, String first, String second, String third, String fourth, boolean moved,
      boolean stopped)
      throws Exception {
    state.putTask("ABC-1", reviewPending);
    state.updateTask(
        "ABC-1",
        handedBack ->
            handedBack
                .withStatus(TaskStatus.REVIEW_PENDING, "plan", true, 1)
                .withLastChangeOrigin(ActionOrigin.MCP)
                .withLastChangeWhy(first));
    verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), delayNeverPinned, acting);
    state.updateTask(
        "ABC-1",
        handedBack ->
            handedBack
                .withStatus(TaskStatus.REVIEW_PENDING, "plan", true, 2)
                .withLastChangeOrigin(ActionOrigin.MCP)
                .withLastChangeWhy(second));
    verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), delayStillNeverPinned, acting);
    state.updateTask(
        "ABC-1",
        handedBack ->
            handedBack
                .withStatus(TaskStatus.REVIEW_PENDING, "plan", true, 3)
                .withLastChangeOrigin(ActionOrigin.MCP)
                .withLastChangeWhy(third));
    verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), noTestPinsDelay, acting);
    state.updateTask(
        "ABC-1",
        handedBack ->
            handedBack
                .withStatus(TaskStatus.REVIEW_PENDING, "plan", true, 4)
                .withLastChangeOrigin(ActionOrigin.MCP)
                .withLastChangeWhy(fourth));

    boolean actual =
        verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), delayNeverPinnedAgain, acting);

    assertThat(actual).isEqualTo(moved);
    assertThat(state.task("ABC-1").orElseThrow().stopped()).isEqualTo(stopped);
  }

  @Test
  void settlesNoRequestForEvidenceSoTheSessionsAnswerCanStillChangeIt() throws Exception {
    state.putTask("ABC-1", reviewPending);

    verdicts.act(
        "ABC-1",
        reviewPending,
        new MasterReview.Verdict(
            MasterReview.Kind.NOT_READY,
            List.of(
                new MasterReview.Said(
                    "chaplain",
                    new Finding(
                        "Api.java",
                        "which caller still reads v2",
                        "",
                        "unproven",
                        "",
                        "behaviour")),
                new MasterReview.Said(
                    "developer",
                    new Finding(
                        "Foo.java", "the guard is inverted", "", "blocking", "", "behaviour"))),
            List.of(),
            List.of(),
            1,
            ""),
        acting);

    verify(decisions)
        .record(reviewPending, "- [developer] Foo.java — the guard is inverted (behaviour)");
  }

  @Test
  void shipsReadyRoundOnlyWhereHumanSaidTheReviewerStandsInForThem() throws Exception {
    state.putTask("ABC-1", reviewPending);
    when(ship.ship("ABC-1", reviewPending)).thenReturn(true);

    boolean moved = verdicts.act("ABC-1", reviewPending, ready, acting);

    assertThat(moved).isTrue();
    verify(ship).ship("ABC-1", reviewPending);
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
  }

  @Test
  void shipsNothingWhereTheHumanKeptThatStep() throws Exception {
    state.putTask("ABC-1", reviewPending);
    var kept = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("ship"), null);

    boolean moved = verdicts.act("ABC-1", reviewPending, ready, kept);

    assertThat(moved).isFalse();
    verify(ship, never()).ship(anyString(), any());
  }

  @Test
  void leavesReadyRoundForTheHumanWhileTheReviewerOnlyJudges() throws Exception {
    state.putTask("ABC-1", reviewPending);

    boolean moved = verdicts.act("ABC-1", reviewPending, ready, judging);

    assertThat(moved).isFalse();
    verify(ship, never()).ship(anyString(), any());
  }

  @Test
  void keepsThePlanGateClosedWhenTheMasterSendsThePlanBack() throws Exception {
    TaskState planned =
        TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING)
            .planGate(PlanGate.REQUIRED)
            .build();
    state.putTask("ABC-1", planned);

    verdicts.act(
        "ABC-1",
        planned,
        new MasterReview.Verdict(
            MasterReview.Kind.NOT_READY,
            List.of(
                new MasterReview.Said(
                    "planner",
                    new Finding("plan.md", "misses the migration", "", "wrong", "", "missing"))),
            List.of(),
            List.of(),
            1,
            ""),
        acting);

    assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.REQUIRED);
  }

  @Test
  void sendsPlanThatMissesTheTicketBackToBeReworked() throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(
        root.resolve("roles/session/brief.md.dist"), "## plan-not-ready\n\nrework the plan\n");
    TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
    state.putTask("ABC-1", planned);

    verdicts.act(
        "ABC-1",
        planned,
        new MasterReview.Verdict(
            MasterReview.Kind.NOT_READY,
            List.of(
                new MasterReview.Said(
                    "planner",
                    new Finding(
                        "plan.md",
                        "drops the v2 route the ticket keeps",
                        "",
                        "wrong",
                        "",
                        "missing"))),
            List.of(),
            List.of(),
            1,
            ""),
        judging);

    verify(sessions)
        .writeTaskContext(
            "ABC-1",
            "- [planner] plan.md — drops the v2 route the ticket keeps (missing)\n\n"
                + "rework the plan");
  }

  @Test
  void putsTheReviewersQuestionToTheHumanThroughTheSessionThatWaitsOnTheAnswer() throws Exception {
    state.putTask("ABC-1", reviewPending);

    boolean moved =
        verdicts.act(
            "ABC-1",
            reviewPending,
            new MasterReview.Verdict(
                MasterReview.Kind.QUESTION,
                List.of(),
                List.of(),
                List.of("Add v3 beside v2, or replace it?"),
                1,
                ""),
            acting);

    assertThat(moved).isTrue();
    assertThat(state.task("ABC-1").orElseThrow())
        .returns(TaskStatus.IN_PROGRESS, TaskState::status)
        .returns(AgentReport.QUESTION, TaskState::report)
        .returns("reviewer: Add v3 beside v2, or replace it?", TaskState::message);
    verify(ship, never()).ship(anyString(), any());
  }

  @Test
  void asksTheSameQuestionOnce() throws Exception {
    state.putTask("ABC-1", reviewPending);
    OriginContext.as(
        ActionOrigin.MASTER,
        () ->
            relay.relay(
                "ABC-1",
                Step.ASKED_HUMAN,
                "The reviewer asked the human: Add v3 beside v2, or replace it?",
                Standing.of(reviewPending)));

    boolean moved =
        verdicts.act(
            "ABC-1",
            state.task("ABC-1").orElseThrow(),
            new MasterReview.Verdict(
                MasterReview.Kind.QUESTION,
                List.of(),
                List.of(),
                List.of("Add v3 beside v2, or replace it?"),
                1,
                ""),
            acting);

    assertThat(moved).isFalse();
    assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
  }

  @Test
  void recordsWhatTheMasterDecidedSoLaterRoundDoesNotReopenIt() throws Exception {
    state.putTask("ABC-1", reviewPending);

    verdicts.answered(
        "ABC-1", reviewPending, "keep v2?", new MasterPanel.Decision("keep v2\nbeside v3", ""));

    verify(decisions).record(reviewPending, "- asked: keep v2? — decided: keep v2 beside v3");
    verify(decisions).answered(reviewPending);
  }

  @Test
  void answersNothingAndRecordsNothingWhenTheSessionAlreadyHoldsThatAnswer() throws Exception {
    state.putTask("ABC-1", reviewPending);
    OriginContext.as(
        ActionOrigin.MASTER,
        () ->
            relay.relay(
                "ABC-1",
                Step.ANSWERED,
                "The Master answered your question, standing in for the human:\nkeep v2 beside v3",
                Standing.of(reviewPending)));

    boolean moved =
        verdicts.answered(
            "ABC-1",
            state.task("ABC-1").orElseThrow(),
            "keep v2?",
            new MasterPanel.Decision("keep v2 beside v3", ""));

    assertThat(moved).isFalse();
    verify(decisions, never()).record(any(), anyString());
  }

  @Test
  void opensNoTaskWhenTheMastersAnswerHoldsDoLine() throws Exception {
    state.putTask("ABC-1", reviewPending);

    verdicts.answered(
        "ABC-1",
        reviewPending,
        "a second request?",
        new MasterPanel.Decision("drop the old keys here", "do proj drop the old keys from main"));

    verify(sessions)
        .writeTaskContext(eq("ABC-1"), contains("Not opened: only the human opens a task."));
  }

  @Test
  void reportsAnAnswerCountOverTheTreeAsTheDecisionsHoldIt() throws Exception {
    state.putTask("ABC-1", reviewPending);
    doReturn(3).when(decisions).answersOverThisTree(reviewPending);

    assertThat(verdicts.answersOverThisTree(reviewPending)).isEqualTo(3);
  }

  @Test
  void movesNothingOfAnAnsweredTaskThatIsGoneFromTheState() throws Exception {

    boolean moved =
        verdicts.answered(
            "ABC-1", reviewPending, "keep v2?", new MasterPanel.Decision("keep v2 beside v3", ""));

    assertThat(moved).isFalse();
  }

  @Test
  void movesNothingWhenTheShipWasNotOpened() throws Exception {
    state.putTask("ABC-1", reviewPending);

    boolean moved = verdicts.act("ABC-1", reviewPending, ready, acting);

    assertThat(moved).isFalse();
  }

  @Test
  void stopsTheTaskInsteadOfReturningFourthRoundOfFreshFindingsRunning() throws Exception {
    state.putTask("ABC-1", reviewPending);
    verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), cartGuardInverted, acting);
    state.updateTask(
        "ABC-1", handedBack -> handedBack.withStatus(TaskStatus.REVIEW_PENDING, "fixed", 1));
    verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), orderGuardInverted, acting);
    state.updateTask(
        "ABC-1", handedBack -> handedBack.withStatus(TaskStatus.REVIEW_PENDING, "fixed", 2));
    boolean third =
        verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), paymentGuardInverted, acting);
    boolean stoppedAfterThird = state.task("ABC-1").orElseThrow().stopped();
    state.updateTask(
        "ABC-1", handedBack -> handedBack.withStatus(TaskStatus.REVIEW_PENDING, "fixed", 3));

    boolean moved =
        verdicts.act("ABC-1", state.task("ABC-1").orElseThrow(), invoiceGuardInverted, acting);

    assertThat(third).isTrue();
    assertThat(stoppedAfterThird).isFalse();
    assertThat(moved).isFalse();
    assertThat(state.task("ABC-1").orElseThrow())
        .extracting(TaskState::status, done -> Objects.requireNonNull(done.stop()).trigger())
        .containsExactly(TaskStatus.REVIEW_PENDING, TaskStop.Trigger.ROUNDS);
  }

  @ParameterizedTest
  @CsvSource({"NOT_READY,REVIEW_PENDING", "QUESTION,REVIEW_PENDING"})
  void movesNothingOfTaskThatIsGoneFromTheState(MasterReview.Kind kind, TaskStatus status)
      throws Exception {
    TaskState task = TaskState.builder("proj", root.toString(), status).alias("a1").build();

    boolean moved =
        verdicts.act(
            "ABC-1",
            task,
            new MasterReview.Verdict(
                kind,
                List.of(
                    new MasterReview.Said(
                        "developer",
                        new Finding("Foo.java", "wrong", "", "blocking", "", "behaviour"))),
                List.of(),
                List.of(),
                1,
                ""),
            acting);

    assertThat(moved).isFalse();
  }
}
