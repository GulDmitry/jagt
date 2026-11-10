package dev.jagt.orchestrator.service;

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

import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
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

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final Clock fixed = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC);

  @Test
  void tellsTheSessionTheStepsFactsThenWhatTheBriefAsks(@TempDir Path root) throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Step.DEPLOY, true)
            .writeBrief(root, "## deploy\n\nopen it in a browser\n");

    fixture.relay.relay(
        "ABC-42",
        Step.DEPLOY,
        "ABC-42 is deployed:\n- proj: 01234567 on dev",
        fixture.relay.standing("ABC-42"));

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
  void relaysNothingWhenTheTurnMovedOnSinceTheTaskWasRead(
      String kind, Entry entry, @TempDir Path root) {
    StepRelayFixture fixture = new StepRelayFixture(root, sessions, Step.DEPLOY, true);
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    final Standing read = Standing.of(fixture.doors.state.task("ABC-42").orElseThrow());
    final StepRelay.Claim claim =
        new StepRelay.Claim(read, opens -> FlowReports.Claimed.LANDED, opened -> true);
    fixture.turns.opened("ABC-42", "answered", "the human answered", TaskStatus.DEPLOYED);
    when(fixture.door.roundDue("ABC-42", "deploy")).thenReturn(true);
    Turn before = fixture.turns.of("ABC-42");

    assertThatThrownBy(() -> entry.relayed(fixture.relay, read, claim))
        .isInstanceOf(TaskMovedOn.class);

    verifyNoInteractions(sessions);
    assertThat(fixture.turns.of("ABC-42")).isEqualTo(before);
  }

  @Test
  void tellsTheSessionNothingWhenTheTaskMovedOnBetweenTheCheckAndTheClaim(@TempDir Path root) {
    StepRelay relay = new StepRelayFixture(root, sessions, Step.DEPLOY, true).relay;
    StepRelay.Claim claim =
        new StepRelay.Claim(
            Standing.of(TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build()),
            opens -> FlowReports.Claimed.MOVED_ON,
            opened -> false);

    assertThatThrownBy(() -> relay.relay("ABC-42", Step.DEPLOY, "ABC-42 is deployed", claim))
        .isInstanceOf(TaskMovedOn.class);

    verify(sessions, never()).writeTaskContext(anyString(), anyString());
  }

  @Test
  void typesTheStepAsOneLine(@TempDir Path root) throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Step.WAKE, true)
            .writeBrief(root, "## wake\n\nContinue where\nyou stopped.\n");

    fixture.relay.say(
        "ABC-42", Step.WAKE, "The usage limit has reset.", fixture.relay.standing("ABC-42"));

    verify(sessions).say("ABC-42", "The usage limit has reset. Continue where you stopped.");
  }

  @Test
  void tellsTheFactsAloneAtStepTheBriefLeavesEmpty(@TempDir Path root) throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Step.DEPLOY, true).writeBrief(root, "## deploy\n");

    fixture.relay.relay(
        "ABC-42",
        Step.DEPLOY,
        "ABC-42 is deployed:\n- proj: 01234567 on dev",
        fixture.relay.standing("ABC-42"));

    verify(sessions).writeTaskContext("ABC-42", "ABC-42 is deployed:\n- proj: 01234567 on dev");
  }

  @Test
  void relaysStepOnceWhileTheSessionHoldsTheTurnItOpened(@TempDir Path root) {
    StepRelayFixture fixture = new StepRelayFixture(root, sessions, Step.DEPLOY, true);
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    fixture.relay.relay(
        "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42"));

    boolean again =
        fixture.relay.relay(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42"));

    assertThat(again).isFalse();
    verify(sessions, times(1)).writeTaskContext("ABC-42", "ABC-42 is deployed");
  }

  @Test
  void sendsStepOnceWhenJagtDiesWhileTellingTheSession(@TempDir Path root) {
    StepRelayFixture fixture = new StepRelayFixture(root, sessions, Step.DEPLOY, true);
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    when(sessions.writeTaskContext("ABC-42", "ABC-42 is deployed"))
        .thenThrow(new Error("jagt died"));
    assertThatThrownBy(
            () ->
                fixture.relay.relay(
                    "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42")))
        .isInstanceOf(Error.class);

    boolean again =
        fixture.relay.relay(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42"));

    assertThat(again).isFalse();
    verify(sessions, times(1)).writeTaskContext("ABC-42", "ABC-42 is deployed");
  }

  @Test
  void sendsStepAgainWhenTellingTheSessionFailed(@TempDir Path root) {
    StepRelayFixture fixture = new StepRelayFixture(root, sessions, Step.DEPLOY, true);
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    when(sessions.writeTaskContext("ABC-42", "ABC-42 is deployed"))
        .thenThrow(new IllegalStateException("window closed"))
        .thenReturn("written");
    assertThatThrownBy(
            () ->
                fixture.relay.relay(
                    "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42")))
        .isInstanceOf(IllegalStateException.class);

    boolean again =
        fixture.relay.relay(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42"));

    assertThat(again).isTrue();
  }

  @Test
  void stopsTheTaskForTheHumanWhenTellingTheSessionFailed(@TempDir Path root) {
    StepRelayFixture fixture = new StepRelayFixture(root, sessions, Step.DEPLOY, true);
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    when(sessions.writeTaskContext("ABC-42", "ABC-42 is deployed"))
        .thenThrow(new IllegalStateException("window closed"));

    assertThatThrownBy(
            () ->
                fixture.relay.relay(
                    "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42")))
        .isInstanceOf(IllegalStateException.class);

    verify(fixture.door)
        .undelivered("ABC-42", "deploy", "java.lang.IllegalStateException: window closed");
  }

  @Test
  void relaysStepCaseSendsAtTheTasksStatus(@TempDir Path root) throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Clock.systemUTC()).writeCases(root, DEPLOY_CASE);
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    fixture.relay.relay(
        "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42"));

    verify(sessions).writeTaskContext("ABC-42", "ABC-42 is deployed");
  }

  @Test
  void sendsNothingAndRecordsMissWhereNoCaseSendsTheStepAtTheTasksStatus(@TempDir Path root)
      throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, fixed).writeCases(root, DEPLOY_CASE);
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    boolean told =
        fixture.relay.relay(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42"));

    assertThat(told).isFalse();
    verify(sessions, never()).writeTaskContext(anyString(), anyString());
    assertThat(Files.readString(root.resolve("misses.jsonl")))
        .isEqualTo(
            "{\"at\":1000,\"task\":\"ABC-42\",\"step\":\"deploy\",\"status\":\"IN_PROGRESS\",\""
                + "by\":\"jagt\"}\n");
  }

  @Test
  void sendsNothingAndRecordsMissWhereTheStepWasSetOffBySomeoneItsCaseDoesNotName(
      @TempDir Path root) throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, fixed).writeCases(root, DEPLOY_CASE);
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        OriginContext.as(
            ActionOrigin.BOARD,
            () ->
                fixture.relay.relay(
                    "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42")));

    assertThat(told).isFalse();
    verify(sessions, never()).writeTaskContext(anyString(), anyString());
    assertThat(Files.readString(root.resolve("misses.jsonl")))
        .isEqualTo(
            "{\"at\":1000,\"task\":\"ABC-42\",\"step\":\"deploy\",\"status\":\"DEPLOYED\",\"by"
                + "\":\"human\"}\n");
  }

  @Test
  void relaysStepSessionsOwnCallSetsOffAsJagtsAnswerToIt(@TempDir Path root) throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Clock.systemUTC()).writeCases(root, DEPLOY_CASE);
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        OriginContext.as(
            ActionOrigin.MCP,
            () ->
                fixture.relay.relay(
                    "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42")));

    assertThat(told).isTrue();
  }

  @Test
  void stopsTheTaskForTheHumanWhereNoCaseSendsTheStepAtItsStatus(@TempDir Path root)
      throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Clock.systemUTC()).writeCases(root, DEPLOY_CASE);
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    fixture.relay.relay(
        "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42"));

    assertThat(fixture.doors.state.task("ABC-42").orElseThrow().stop())
        .extracting(TaskStop::trigger, TaskStop::evidence)
        .containsExactly(
            TaskStop.Trigger.MISS, "no case sends deploy at IN_PROGRESS, set off by jagt");
  }

  @Test
  void stopsTheTaskInsteadOfSendingFourthRedVerificationRunning(@TempDir Path root)
      throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Clock.systemUTC()).writeCases(root, VERIFY_CASE);
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
    fixture.relayedVerifyRounds(
        "BUILD FAILED in CartTest", "BUILD FAILED in OrderTest", "BUILD FAILED in CartTest again");

    boolean told =
        fixture.relay.relayRound(
            "ABC-42", Step.VERIFY, "BUILD FAILED in PaymentTest", fixture.relay.standing("ABC-42"));

    assertThat(told).isFalse();
    verify(sessions, times(3)).writeTaskContext(anyString(), anyString());
    assertThat(fixture.doors.state.task("ABC-42").orElseThrow().stop())
        .extracting(TaskStop::trigger, TaskStop::evidence)
        .containsExactly(
            TaskStop.Trigger.ROUNDS, "verify sent 3 rounds running in 24h; the next one was not");
  }

  @Test
  void stopsTheTaskInsteadOfSendingFourthReviewRoundRunning(@TempDir Path root) {
    StepRelayFixture fixture = new StepRelayFixture(root, sessions, Clock.systemUTC());
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    List<Step> steps = List.of(Step.ROUND_THREADS, Step.ROUND_THREADS_DONE);
    fixture.relay.relayComposed(
        "ABC-42",
        steps,
        "mia: Why is this loop not bounded?",
        TaskStatus.REVIEWED,
        fixture.relay.standing("ABC-42"));
    fixture.relay.relayComposed(
        "ABC-42",
        steps,
        "mia: Why is this map not sorted?",
        TaskStatus.REVIEWED,
        fixture.relay.standing("ABC-42"));
    fixture.relay.relayComposed(
        "ABC-42",
        steps,
        "mia: Why is this name so long?",
        TaskStatus.REVIEWED,
        fixture.relay.standing("ABC-42"));

    boolean told =
        fixture.relay.relayComposed(
            "ABC-42",
            steps,
            "mia: Why is this test so short?",
            TaskStatus.REVIEWED,
            fixture.relay.standing("ABC-42"));

    assertThat(told).isFalse();
    verify(sessions, times(3)).relayFresh(anyString(), anyString());
    assertThat(fixture.doors.state.task("ABC-42").orElseThrow().stop())
        .extracting(TaskStop::trigger, TaskStop::evidence)
        .containsExactly(
            TaskStop.Trigger.ROUNDS,
            "round-threads+round-threads-done sent 3 rounds running in 24h; the next one was not");
  }

  @Test
  void sendsThirdRedVerificationRunning(@TempDir Path root) throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Clock.systemUTC()).writeCases(root, VERIFY_CASE);
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).build());
    fixture.relayedVerifyRounds("BUILD FAILED in CartTest", "BUILD FAILED in OrderTest");

    boolean told =
        fixture.relay.relayRound(
            "ABC-42", Step.VERIFY, "BUILD FAILED in PaymentTest", fixture.relay.standing("ABC-42"));

    assertThat(told).isTrue();
    assertThat(fixture.doors.state.task("ABC-42").orElseThrow().stopped()).isFalse();
  }

  @Test
  void relaysTheMastersAnswerWhereItsSessionCaseSendsIt(@TempDir Path root) throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Clock.systemUTC())
            .writeCases(
                root,
                """
                ## answered

                step: answered
                at: IN_PROGRESS
                by: master
                replies: any; plain
                The Master answers.
                """);
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    boolean told =
        OriginContext.as(
            ActionOrigin.MASTER,
            () ->
                fixture.relay.relay(
                    "ABC-42",
                    Step.ANSWERED,
                    "Keep the old keys.",
                    fixture.relay.standing("ABC-42")));

    assertThat(told).isTrue();
  }

  @Test
  void sendsTheTextWrittenWhereCaseSendsItAndOpensTheTurn(@TempDir Path root) throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Step.DEPLOY, true).writeBrief(root, "## deploy\n");
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        fixture.relay.write(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42"));

    assertThat(told).isEqualTo(true);
    verify(sessions).writeTaskContext("ABC-42", "ABC-42 is deployed");
    assertThat(fixture.doors.state.task("ABC-42").orElseThrow().turnOrNone().open())
        .isEqualTo(true);
  }

  @Test
  void leavesTheTextWrittenUnsentAndOpensNoTurnWhereNoCaseSendsIt(@TempDir Path root)
      throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Step.DEPLOY, false).writeBrief(root, "## deploy\n");
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        fixture.relay.write(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42"));

    assertThat(told).isEqualTo(false);
    verify(sessions, never()).writeTaskContext(anyString(), anyString());
    assertThat(fixture.doors.state.task("ABC-42").orElseThrow().turnOrNone().open())
        .isEqualTo(false);
  }

  @Test
  void sendsTheLineTypedWhereCaseSendsItAndOpensTheTurn(@TempDir Path root) throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Step.DEPLOY, true).writeBrief(root, "## deploy\n");
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        fixture.relay.say(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42"));

    assertThat(told).isEqualTo(true);
    verify(sessions).say("ABC-42", "ABC-42 is deployed");
    assertThat(fixture.doors.state.task("ABC-42").orElseThrow().turnOrNone().open())
        .isEqualTo(true);
  }

  @Test
  void leavesTheLineTypedUnsentAndOpensNoTurnWhereNoCaseSendsIt(@TempDir Path root)
      throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Step.DEPLOY, false).writeBrief(root, "## deploy\n");
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        fixture.relay.say(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", fixture.relay.standing("ABC-42"));

    assertThat(told).isEqualTo(false);
    verify(sessions, never()).say(anyString(), anyString());
    assertThat(fixture.doors.state.task("ABC-42").orElseThrow().turnOrNone().open())
        .isEqualTo(false);
  }

  @Test
  void sendsBelowPendingInstructionWhereCaseSendsItAndOpensTheTurn(@TempDir Path root)
      throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Step.DEPLOY, true).writeBrief(root, "## deploy\n");
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        fixture.relay.relayUnder(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", true, fixture.relay.standing("ABC-42"));

    assertThat(told).isEqualTo(true);
    verify(sessions).relayUnder("ABC-42", "ABC-42 is deployed", true);
    assertThat(fixture.doors.state.task("ABC-42").orElseThrow().turnOrNone().open())
        .isEqualTo(true);
  }

  @Test
  void leavesBelowPendingInstructionUnsentAndOpensNoTurnWhereNoCaseSendsIt(@TempDir Path root)
      throws Exception {
    StepRelayFixture fixture =
        new StepRelayFixture(root, sessions, Step.DEPLOY, false).writeBrief(root, "## deploy\n");
    fixture.doors.state.putTask(
        "ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());

    boolean told =
        fixture.relay.relayUnder(
            "ABC-42", Step.DEPLOY, "ABC-42 is deployed", true, fixture.relay.standing("ABC-42"));

    assertThat(told).isEqualTo(false);
    verify(sessions, never()).relayUnder(anyString(), anyString(), anyBoolean());
    assertThat(fixture.doors.state.task("ABC-42").orElseThrow().turnOrNone().open())
        .isEqualTo(false);
  }
}
