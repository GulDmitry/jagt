package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestCaseDoorBuilder.caseDoor;
import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StepRelayTest {

  private static final String DEPLOY_CASE =
      """
      ## deploy

      step: deploy
      at: DEPLOYED
      by: jagt
      replies: any; plain
      The task is live.
      """;

  private static final String VERIFY_CASE =
      """
      ## verify

      step: verify
      at: VERIFYING
      by: jagt
      replies: any; plain
      The command failed.
      """;

  @TempDir Path root;

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final Clock fixed = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC);
  private final CaseDoor door = mock(CaseDoor.class);
  private OrchestratorPaths paths;
  private StateService state;
  private CaseDoor openDoor;
  private TaskTurns turns;
  private StepRelay relay;
  private StepRelay admittingRelay;

  @BeforeEach
  void setUp() throws IOException {
    paths = orchestratorPaths().withRoot(root).build();
    state = stateService().withRoot(root).build();
    openDoor =
        caseDoor()
            .withRoot(root)
            .build(state, taskStops().withRoot(root).build(state, fixed), fixed);
    turns = new TaskTurns(state, fixed, new Checkers(new TaskChart(Composition.builtIn())));
    relay = new StepRelay(new SessionBrief(paths), sessions, openDoor, turns);
    admittingRelay = new StepRelay(new SessionBrief(paths), sessions, door, turns);
    Files.createDirectories(root.resolve("roles/session"));
  }

  @Test
  void tellsTheSessionTheStepsFactsThenWhatTheBriefAsks() throws Exception {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
    Files.writeString(
        root.resolve("roles/session/brief.md"), "## deploy\n\nopen it in a browser\n");

    admittingRelay.relay(
        "ABC-42",
        Step.DEPLOY,
        "ABC-42 is deployed:\n- proj: 01234567 on dev",
        admittingRelay.standing("ABC-42"));

    verify(sessions)
        .writeTaskContext(
            "ABC-42", "ABC-42 is deployed:\n- proj: 01234567 on dev\n\nopen it in a browser");
  }

  interface Entry {
    boolean relayed(StepRelay relay, Standing read, StepRelay.Claim claim);
  }

  static Stream<Arguments> entries() {
    return Stream.of(
        Arguments.of(
            "relay",
            (Entry)
                (relay, read, claim) ->
                    relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", read)),
        Arguments.of(
            "relayRound",
            (Entry)
                (relay, read, claim) ->
                    relay.relayRound("ABC-42", Step.DEPLOY, "ABC-42 is deployed", read)),
        Arguments.of(
            "relayUnder",
            (Entry)
                (relay, read, claim) ->
                    relay.relayUnder("ABC-42", Step.DEPLOY, "ABC-42 is deployed", false, read)),
        Arguments.of(
            "relay with a claim",
            (Entry)
                (relay, read, claim) ->
                    relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", claim)),
        Arguments.of(
            "relayRound with a claim",
            (Entry)
                (relay, read, claim) ->
                    relay.relayRound("ABC-42", Step.DEPLOY, "ABC-42 is deployed", claim)),
        Arguments.of(
            "relayUnder with a claim",
            (Entry)
                (relay, read, claim) ->
                    relay.relayUnder("ABC-42", Step.DEPLOY, "ABC-42 is deployed", false, claim)),
        Arguments.of(
            "write",
            (Entry)
                (relay, read, claim) ->
                    relay.write("ABC-42", Step.DEPLOY, "ABC-42 is deployed", read)),
        Arguments.of(
            "say",
            (Entry)
                (relay, read, claim) ->
                    relay.say("ABC-42", Step.DEPLOY, "ABC-42 is deployed", read)),
        Arguments.of(
            "relayComposed",
            (Entry)
                (relay, read, claim) ->
                    relay.relayComposed(
                        "ABC-42",
                        List.of(Step.DEPLOY),
                        "ABC-42 is deployed",
                        TaskStatus.REVIEWED,
                        read)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("entries")
  void relaysNothingWhenTheTurnMovedOnSinceTheTaskWasRead(String kind, Entry entry) {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    final Standing read = Standing.of(state.task("ABC-42").orElseThrow());
    final StepRelay.Claim claim =
        new StepRelay.Claim(read, opens -> FlowReports.Claimed.LANDED, opened -> true);
    turns.opened("ABC-42", "answered", "the human answered", TaskStatus.DEPLOYED);
    when(door.roundDue("ABC-42", "deploy")).thenReturn(true);
    Turn before = turns.of("ABC-42");

    assertThatThrownBy(() -> entry.relayed(admittingRelay, read, claim))
        .isInstanceOf(TaskMovedOn.class);

    verifyNoInteractions(sessions);
    assertThat(turns.of("ABC-42")).isEqualTo(before);
  }

  @Test
  void tellsTheSessionNothingWhenTheTaskMovedOnBetweenTheCheckAndTheClaim() {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
    StepRelay.Claim claim =
        new StepRelay.Claim(
            Standing.of(TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()),
            opens -> FlowReports.Claimed.MOVED_ON,
            opened -> false);

    assertThatThrownBy(
            () -> admittingRelay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", claim))
        .isInstanceOf(TaskMovedOn.class);

    verify(sessions, never()).writeTaskContext(anyString(), anyString());
  }

  @Test
  void typesTheStepAsOneLine() throws Exception {
    when(door.opens("ABC-42", Step.WAKE))
        .thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
    Files.writeString(
        root.resolve("roles/session/brief.md"), "## wake\n\nContinue where\nyou stopped.\n");

    admittingRelay.say(
        "ABC-42", Step.WAKE, "The usage limit has reset.", admittingRelay.standing("ABC-42"));

    verify(sessions).say("ABC-42", "The usage limit has reset. Continue where you stopped.");
  }

  @Test
  void tellsTheFactsAloneAtStepTheBriefLeavesEmpty() throws Exception {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
    Files.writeString(root.resolve("roles/session/brief.md"), "## deploy\n");

    admittingRelay.relay(
        "ABC-42",
        Step.DEPLOY,
        "ABC-42 is deployed:\n- proj: 01234567 on dev",
        admittingRelay.standing("ABC-42"));

    verify(sessions).writeTaskContext("ABC-42", "ABC-42 is deployed:\n- proj: 01234567 on dev");
  }

  @Test
  void relaysStepOnceWhileTheSessionHoldsTheTurnItOpened() {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    admittingRelay.relay(
        "ABC-42", Step.DEPLOY, "ABC-42 is deployed", admittingRelay.standing("ABC-42"));

    boolean again =
        admittingRelay.relay(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", admittingRelay.standing("ABC-42"));

    assertThat(again).isFalse();
    verify(sessions, times(1)).writeTaskContext("ABC-42", "ABC-42 is deployed");
  }

  @Test
  void sendsStepOnceWhenJagtDiesWhileTellingTheSession() {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    when(sessions.writeTaskContext("ABC-42", "ABC-42 is deployed"))
        .thenThrow(new Error("jagt died"));
    assertThatThrownBy(() ->
            admittingRelay.relay(
                "ABC-42", Step.DEPLOY, "ABC-42 is deployed", admittingRelay.standing("ABC-42")))
        .isInstanceOf(Error.class);

    boolean again =
        admittingRelay.relay(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", admittingRelay.standing("ABC-42"));

    assertThat(again).isFalse();
    verify(sessions, times(1)).writeTaskContext("ABC-42", "ABC-42 is deployed");
  }

  @Test
  void sendsStepAgainWhenTellingTheSessionFailed() {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    when(sessions.writeTaskContext("ABC-42", "ABC-42 is deployed"))
        .thenThrow(new IllegalStateException("window closed"))
        .thenReturn("written");
    assertThatThrownBy(() ->
            admittingRelay.relay(
                "ABC-42", Step.DEPLOY, "ABC-42 is deployed", admittingRelay.standing("ABC-42")))
        .isInstanceOf(IllegalStateException.class);

    boolean again =
        admittingRelay.relay(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", admittingRelay.standing("ABC-42"));

    assertThat(again).isTrue();
  }

  @Test
  void stopsTheTaskForTheHumanWhenTellingTheSessionFailed() {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    when(sessions.writeTaskContext("ABC-42", "ABC-42 is deployed"))
        .thenThrow(new IllegalStateException("window closed"));

    assertThatThrownBy(() ->
            admittingRelay.relay(
                "ABC-42", Step.DEPLOY, "ABC-42 is deployed", admittingRelay.standing("ABC-42")))
        .isInstanceOf(IllegalStateException.class);

    verify(door).undelivered("ABC-42", "deploy", "java.lang.IllegalStateException: window closed");
  }

  @Test
  void relaysStepCaseSendsAtTheTasksStatus() throws Exception {
    Files.writeString(root.resolve("roles/session/cases.md"), DEPLOY_CASE);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

    verify(sessions).writeTaskContext("ABC-42", "ABC-42 is deployed");
  }

  @Test
  void sendsNothingAndRecordsMissWhereNoCaseSendsTheStepAtTheTasksStatus() throws Exception {
    Files.writeString(root.resolve("roles/session/cases.md"), DEPLOY_CASE);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    boolean told =
        relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

    assertThat(told).isFalse();
    verify(sessions, never()).writeTaskContext(anyString(), anyString());
    assertThat(Files.readString(root.resolve("misses.jsonl")))
        .isEqualTo(
            "{\"at\":1000,\"task\":\"ABC-42\",\"step\":\"deploy\",\"status\":\"IN_PROGRESS\",\""
                + "by\":\"jagt\"}\n");
  }

  @Test
  void sendsNothingAndRecordsMissWhereTheStepWasSetOffBySomeoneItsCaseDoesNotName()
      throws Exception {
    Files.writeString(root.resolve("roles/session/cases.md"), DEPLOY_CASE);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        OriginContext.as(
            ActionOrigin.BOARD,
            () ->
                relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42")));

    assertThat(told).isFalse();
    verify(sessions, never()).writeTaskContext(anyString(), anyString());
    assertThat(Files.readString(root.resolve("misses.jsonl")))
        .isEqualTo(
            "{\"at\":1000,\"task\":\"ABC-42\",\"step\":\"deploy\",\"status\":\"DEPLOYED\",\"by"
                + "\":\"human\"}\n");
  }

  @Test
  void relaysStepSessionsOwnCallSetsOffAsJagtsAnswerToIt() throws Exception {
    Files.writeString(root.resolve("roles/session/cases.md"), DEPLOY_CASE);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        OriginContext.as(
            ActionOrigin.MCP,
            () ->
                relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42")));

    assertThat(told).isTrue();
  }

  @Test
  void stopsTheTaskForTheHumanWhereNoCaseSendsTheStepAtItsStatus() throws Exception {
    Files.writeString(root.resolve("roles/session/cases.md"), DEPLOY_CASE);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", relay.standing("ABC-42"));

    assertThat(state.task("ABC-42").orElseThrow().stop())
        .extracting(TaskStop::trigger, TaskStop::evidence)
        .containsExactly(
            TaskStop.Trigger.MISS, "no case sends deploy at IN_PROGRESS, set off by jagt");
  }

  @Test
  void stopsTheTaskInsteadOfSendingFourthReviewRoundRunning() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    List<Step> steps = List.of(Step.ROUND_THREADS, Step.ROUND_THREADS_DONE);
    relay.relayComposed(
        "ABC-42",
        steps,
        "mia: Why is this loop not bounded?",
        TaskStatus.REVIEWED,
        relay.standing("ABC-42"));
    relay.relayComposed(
        "ABC-42",
        steps,
        "mia: Why is this map not sorted?",
        TaskStatus.REVIEWED,
        relay.standing("ABC-42"));
    relay.relayComposed(
        "ABC-42",
        steps,
        "mia: Why is this name so long?",
        TaskStatus.REVIEWED,
        relay.standing("ABC-42"));

    boolean told =
        relay.relayComposed(
            "ABC-42",
            steps,
            "mia: Why is this test so short?",
            TaskStatus.REVIEWED,
            relay.standing("ABC-42"));

    assertThat(told).isFalse();
    verify(sessions, times(3)).relayFresh(anyString(), anyString());
    assertThat(state.task("ABC-42").orElseThrow().stop())
        .extracting(TaskStop::trigger, TaskStop::evidence)
        .containsExactly(
            TaskStop.Trigger.ROUNDS,
            "round-threads+round-threads-done sent 3 rounds running in 24h; the next one was not");
  }

  @Test
  void relaysTheMastersAnswerWhereItsSessionCaseSendsIt() throws Exception {
    Files.writeString(
        root.resolve("roles/session/cases.md"),
        """
        ## answered

        step: answered
        at: IN_PROGRESS
        by: master
        replies: any; plain
        The Master answers.
        """);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    boolean told =
        OriginContext.as(
            ActionOrigin.MASTER,
            () ->
                relay.relay(
                    "ABC-42", Step.ANSWERED, "Keep the old keys.", relay.standing("ABC-42")));

    assertThat(told).isTrue();
  }

  @Test
  void sendsTheTextWrittenWhereCaseSendsItAndOpensTheTurn() throws Exception {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
    Files.writeString(root.resolve("roles/session/brief.md"), "## deploy\n");
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        admittingRelay.write(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", admittingRelay.standing("ABC-42"));

    assertThat(told).isEqualTo(true);
    verify(sessions).writeTaskContext("ABC-42", "ABC-42 is deployed");
    assertThat(state.task("ABC-42").orElseThrow().turnOrNone().open()).isEqualTo(true);
  }

  @Test
  void leavesTheTextWrittenUnsentAndOpensNoTurnWhereNoCaseSendsIt() throws Exception {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(false, TaskStatus.REVIEWED));
    Files.writeString(root.resolve("roles/session/brief.md"), "## deploy\n");
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        admittingRelay.write(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", admittingRelay.standing("ABC-42"));

    assertThat(told).isEqualTo(false);
    verify(sessions, never()).writeTaskContext(anyString(), anyString());
    assertThat(state.task("ABC-42").orElseThrow().turnOrNone().open()).isEqualTo(false);
  }

  @Test
  void sendsTheLineTypedWhereCaseSendsItAndOpensTheTurn() throws Exception {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
    Files.writeString(root.resolve("roles/session/brief.md"), "## deploy\n");
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        admittingRelay.say(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", admittingRelay.standing("ABC-42"));

    assertThat(told).isEqualTo(true);
    verify(sessions).say("ABC-42", "ABC-42 is deployed");
    assertThat(state.task("ABC-42").orElseThrow().turnOrNone().open()).isEqualTo(true);
  }

  @Test
  void leavesTheLineTypedUnsentAndOpensNoTurnWhereNoCaseSendsIt() throws Exception {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(false, TaskStatus.REVIEWED));
    Files.writeString(root.resolve("roles/session/brief.md"), "## deploy\n");
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        admittingRelay.say(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", admittingRelay.standing("ABC-42"));

    assertThat(told).isEqualTo(false);
    verify(sessions, never()).say(anyString(), anyString());
    assertThat(state.task("ABC-42").orElseThrow().turnOrNone().open()).isEqualTo(false);
  }

  @Test
  void sendsBelowPendingInstructionWhereCaseSendsItAndOpensTheTurn() throws Exception {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(true, TaskStatus.REVIEWED));
    Files.writeString(root.resolve("roles/session/brief.md"), "## deploy\n");
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        admittingRelay.relayUnder(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", true, admittingRelay.standing("ABC-42"));

    assertThat(told).isEqualTo(true);
    verify(sessions).relayUnder("ABC-42", "ABC-42 is deployed", true);
    assertThat(state.task("ABC-42").orElseThrow().turnOrNone().open()).isEqualTo(true);
  }

  @Test
  void leavesBelowPendingInstructionUnsentAndOpensNoTurnWhereNoCaseSendsIt() throws Exception {
    when(door.opens("ABC-42", Step.DEPLOY))
        .thenReturn(new CaseDoor.Admission(false, TaskStatus.REVIEWED));
    Files.writeString(root.resolve("roles/session/brief.md"), "## deploy\n");
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        admittingRelay.relayUnder(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", true, admittingRelay.standing("ABC-42"));

    assertThat(told).isEqualTo(false);
    verify(sessions, never()).relayUnder(anyString(), anyString(), anyBoolean());
    assertThat(state.task("ABC-42").orElseThrow().turnOrNone().open()).isEqualTo(false);
  }

  @Nested
  class AfterTwoRedVerificationRounds {

    @BeforeEach
    void twoRoundsWereSent() throws Exception {
      Files.writeString(root.resolve("roles/session/cases.md"), VERIFY_CASE);
      state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
      relay.relayRound("ABC-42", Step.VERIFY, "BUILD FAILED in CartTest", relay.standing("ABC-42"));
      relay.relayRound(
          "ABC-42", Step.VERIFY, "BUILD FAILED in OrderTest", relay.standing("ABC-42"));
    }

    @Test
    void stopsTheTaskInsteadOfSendingFourthRedVerificationRunning() {
      relay.relayRound(
          "ABC-42", Step.VERIFY, "BUILD FAILED in CartTest again", relay.standing("ABC-42"));

      boolean told =
          relay.relayRound(
              "ABC-42", Step.VERIFY, "BUILD FAILED in PaymentTest", relay.standing("ABC-42"));

      assertThat(told).isFalse();
      verify(sessions, times(3)).writeTaskContext(anyString(), anyString());
      assertThat(state.task("ABC-42").orElseThrow().stop())
          .extracting(TaskStop::trigger, TaskStop::evidence)
          .containsExactly(
              TaskStop.Trigger.ROUNDS, "verify sent 3 rounds running in 24h; the next one was not");
    }

    @Test
    void sendsThirdRedVerificationRunning() {
      boolean told =
          relay.relayRound(
              "ABC-42", Step.VERIFY, "BUILD FAILED in PaymentTest", relay.standing("ABC-42"));

      assertThat(told).isTrue();
      assertThat(state.task("ABC-42").orElseThrow().stopped()).isFalse();
    }
  }
}
