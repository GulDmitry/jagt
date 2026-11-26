package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TaskReducerSessionTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @Test
  void clearsPermissionTheSignStampedWhenWorkStartsAgain() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState asked =
        reducer.signed(
            task,
            new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK),
            1_000);

    TaskState started = reducer.signed(asked, new SessionSign.WorkStarted(), 2_000);

    assertThat(asked.standing().report()).isEqualTo(AgentReport.PERMISSION);
    assertThat(started.standing().report()).isEqualTo(AgentReport.PLAIN);
  }

  @Test
  void keepsPermissionTheSessionSaidItselfWhenWorkStartsAgain() {
    TaskState task =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .report(AgentReport.PERMISSION)
            .build();
    TaskState asked =
        reducer.signed(
            task,
            new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK),
            1_000);

    TaskState started = reducer.signed(asked, new SessionSign.WorkStarted(), 2_000);

    assertThat(started.standing().report()).isEqualTo(AgentReport.PERMISSION);
  }

  @Test
  void keepsPermissionTheSessionReportedAfterPromptStampedItWhenWorkStartsAgain() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState asked =
        reducer.signed(
            task,
            new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.HOOK),
            1_000);
    TaskState reported = reducer.signed(asked, new SessionSign.Reported(), 2_000);

    TaskState started = reducer.signed(reported, new SessionSign.WorkStarted(), 3_000);

    assertThat(started.standing().report()).isEqualTo(AgentReport.PERMISSION);
  }

  @Test
  void leavesQuestionTheSessionSaidWhenPromptIsReported() {
    TaskState task =
        TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
            .report(AgentReport.QUESTION)
            .build();

    TaskState asked =
        reducer.signed(
            task,
            new SessionSign.Asked("needs your permission", SessionSign.Asked.Channel.SCREEN),
            1_000);

    assertThat(asked.standing().report()).isEqualTo(AgentReport.QUESTION);
  }

  @Test
  void keepsTurnFailureWhenAnIdleNoticeFollowsIt() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState noticed = reducer.signed(failed, new SessionSign.TurnEnded(null), 2_000);

    assertThat(SessionPhase.of(noticed.work().session())).isEqualTo(SessionPhase.FAILED);
    assertThat(Objects.requireNonNull(noticed.work().session().halt()).cause())
        .isEqualTo("overloaded");
  }

  @Test
  void endsTurnFailureWhenTheSessionRestarts() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState restarted = reducer.signed(failed, new SessionSign.Restarted("compact"), 2_000);

    assertThat(SessionPhase.of(restarted.work().session())).isEqualTo(SessionPhase.IDLE);
  }

  @Test
  void worksAgainWhenFailedSessionStartsWork() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState started = reducer.signed(failed, new SessionSign.WorkStarted(), 2_000);

    assertThat(SessionPhase.of(started.work().session())).isEqualTo(SessionPhase.WORKING);
    assertThat(started.work().session().workStartedAt()).isEqualTo(2_000);
  }

  @Test
  void isGoneWithItsReasonWhenTheSessionEnds() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();

    TaskState ended = reducer.signed(task, new SessionSign.Ended("prompt_input_exit"), 1_000);

    assertThat(SessionPhase.of(ended.work().session())).isEqualTo(SessionPhase.GONE);
    assertThat(Objects.requireNonNull(ended.work().session().halt()).cause())
        .isEqualTo("prompt_input_exit");
  }

  @Test
  void keepsTheLogFileSessionNamedWithoutMovingItsPhase() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState idle = reducer.signed(task, new SessionSign.TurnEnded(null), 1_000);

    TaskState named = reducer.signed(idle, new SessionSign.LogNamed("/logs/s.jsonl"), 2_000);

    assertThat(named.work().session().log()).isEqualTo("/logs/s.jsonl");
    assertThat(named.work().session().idleSince()).isEqualTo(1_000);
    assertThat(SessionPhase.of(named.work().session())).isEqualTo(SessionPhase.IDLE);
  }

  @Test
  void isIdleWhenTurnEndsWhileWorking() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState working = reducer.signed(task, new SessionSign.WorkStarted(), 1_000);

    TaskState idle = reducer.signed(working, new SessionSign.TurnEnded("/logs/s.jsonl"), 2_000);

    assertThat(SessionPhase.of(idle.work().session())).isEqualTo(SessionPhase.IDLE);
    assertThat(idle.work().session().log()).isEqualTo("/logs/s.jsonl");
  }

  @ParameterizedTest
  @ValueSource(longs = {500, 1_000})
  void keepsHaltWhenCallIsNoNewerThanIt(long calledAt) {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState called = reducer.signed(failed, new SessionSign.Called(), calledAt);

    assertThat(SessionPhase.of(called.work().session())).isEqualTo(SessionPhase.FAILED);
  }

  @Test
  void dropsHaltWhenCallComesAfterIt() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState ended = reducer.signed(task, new SessionSign.Ended("prompt_input_exit"), 1_000);

    TaskState called = reducer.signed(ended, new SessionSign.Called(), 2_000);

    assertThat(SessionPhase.of(called.work().session())).isEqualTo(SessionPhase.WORKING);
  }

  @Test
  void sessionThatOnlyRunsToolsIsNotQuietPastTheLimit() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState working = reducer.signed(task, new SessionSign.WorkStarted(), 1_000);
    TaskState tooled = reducer.signed(working, new SessionSign.ToolUsed(), 8_000);

    TaskState read =
        reducer.signed(
            tooled, new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 5_000, null), 9_000);

    assertThat(SessionPhase.of(read.work().session())).isEqualTo(SessionPhase.WORKING);
    assertThat(read.work().session().quietSince()).isZero();
  }

  @ParameterizedTest
  @ValueSource(longs = {500, 1_000})
  void keepsHaltWhenToolUseIsNoNewerThanIt(long usedAt) {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState used = reducer.signed(failed, new SessionSign.ToolUsed(), usedAt);

    assertThat(SessionPhase.of(used.work().session())).isEqualTo(SessionPhase.FAILED);
  }

  @Test
  void dropsHaltWhenToolUseComesAfterIt() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState used = reducer.signed(failed, new SessionSign.ToolUsed(), 2_000);

    assertThat(SessionPhase.of(used.work().session())).isEqualTo(SessionPhase.WORKING);
  }

  @Test
  void toolUseIsNoReportOfTheTurn() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState working = reducer.signed(task, new SessionSign.WorkStarted(), 1_000);

    TaskState used = reducer.signed(working, new SessionSign.ToolUsed(), 2_000);

    assertThat(used.work().session().reportedAt()).isZero();
    assertThat(used.work().session().workStartedAt()).isEqualTo(1_000);
  }

  @Test
  void dropsHaltWhenWorkStartsInTheSameMillisecond() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState started = reducer.signed(failed, new SessionSign.WorkStarted(), 1_000);

    assertThat(SessionPhase.of(started.work().session())).isEqualTo(SessionPhase.WORKING);
  }

  @Test
  void goesQuietSinceTheTurnEndedWhenReadFindsNothingAfterItForLongerThanTheLimit() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState idle = reducer.signed(task, new SessionSign.TurnEnded(null), 1_000);

    TaskState quiet =
        reducer.signed(
            idle, new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 5_000, null), 9_000);

    assertThat(SessionPhase.of(quiet.work().session())).isEqualTo(SessionPhase.QUIET);
    assertThat(quiet.work().session().quietSince()).isEqualTo(1_000);
  }

  @Test
  void sessionStartedOverIsNotEndedByTheEndSignOfTheCliItReplaced() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState fresh = reducer.sessionStartedOver(task);

    TaskState late = reducer.signed(fresh, new SessionSign.Ended("exited"), 9_000);

    assertThat(SessionPhase.of(late.work().session())).isNotEqualTo(SessionPhase.GONE);
  }

  @Test
  void sessionStartedOverIsNotQuietFromTheOldTranscriptBeforeItsFirstHook() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState fresh = reducer.sessionStartedOver(task);

    TaskState first =
        reducer.signed(
            fresh,
            new SessionSign.Read(Session.Window.State.RUNNING, 0, 1_000, 0, 5_000, null),
            9_000);
    TaskState second =
        reducer.signed(
            first,
            new SessionSign.Read(Session.Window.State.RUNNING, 0, 1_000, 0, 5_000, null),
            12_000);
    TaskState third =
        reducer.signed(
            second,
            new SessionSign.Read(Session.Window.State.RUNNING, 0, 1_000, 0, 5_000, null),
            14_000);

    assertThat(first.work().session().quietSince()).isZero();
    assertThat(second.work().session().quietSince()).isZero();
    assertThat(third.work().session().quietSince()).isEqualTo(9_000);
  }

  @Test
  void staysIdleWhenReadFindsTheTurnEndWithinTheLimit() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState idle = reducer.signed(task, new SessionSign.TurnEnded(null), 1_000);

    TaskState read =
        reducer.signed(
            idle, new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 5_000, null), 4_000);

    assertThat(SessionPhase.of(read.work().session())).isEqualTo(SessionPhase.IDLE);
  }

  @Test
  void staysIdleAndGoesQuietSinceTheTurnEndedWhenTheWindowOnlyRepaintedAfterIt() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState idle = reducer.signed(task, new SessionSign.TurnEnded(null), 1_000);

    TaskState quiet =
        reducer.signed(
            idle,
            new SessionSign.Read(Session.Window.State.RUNNING, 2_000, 0, 0, 5_000, null),
            9_000);

    assertThat(Objects.requireNonNull(quiet.work().session().halt()).kind())
        .isEqualTo(Session.Halt.Kind.IDLE);
    assertThat(quiet.work().session().quietSince()).isEqualTo(1_000);
  }

  @Test
  void goesQuietSinceTheLogWriteWhenTheWindowOnlyRepaintedAfterLogWriteDroppedTheTurnEnd() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState idle = reducer.signed(task, new SessionSign.TurnEnded(null), 1_000);

    TaskState quiet =
        reducer.signed(
            idle,
            new SessionSign.Read(Session.Window.State.RUNNING, 8_000, 1_500, 0, 5_000, null),
            9_000);

    assertThat(quiet.work().session().quietSince()).isEqualTo(1_500);
  }

  @Test
  void staysWorkingWhenTheWindowPrintedRecentlyAndNoTurnEverEnded() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState working = reducer.signed(task, new SessionSign.WorkStarted(), 1_000);

    TaskState read =
        reducer.signed(
            working,
            new SessionSign.Read(Session.Window.State.RUNNING, 8_000, 0, 0, 5_000, null),
            9_000);

    assertThat(read.work().session().quietSince()).isZero();
  }

  @Test
  void endsAnEndedSessionWhenLaterReadFindsItsAgentProcessRunning() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState ended = reducer.signed(task, new SessionSign.Ended("boom"), 1_000);

    TaskState read =
        reducer.signed(
            ended,
            new SessionSign.Read(Session.Window.State.RUNNING, 8_000, 0, 0, 5_000, null),
            9_000);

    assertThat(SessionPhase.of(read.work().session())).isNotEqualTo(SessionPhase.GONE);
  }

  @Test
  void endsGoneSessionReadSetWhenLaterReadFindsItsAgentProcessRunning() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState gone =
        reducer.signed(
            task, new SessionSign.Read(Session.Window.State.MISSING, 0, 0, 0, 5_000, null), 2_000);

    TaskState read =
        reducer.signed(
            gone, new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 0, 5_000, null), 3_000);

    assertThat(SessionPhase.of(read.work().session())).isNotEqualTo(SessionPhase.GONE);
  }

  @Test
  void keepsFailedSessionFailedWhenLaterReadFindsItsAgentProcessRunning() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState read =
        reducer.signed(
            failed,
            new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 0, 5_000, null),
            3_000);

    assertThat(SessionPhase.of(read.work().session())).isEqualTo(SessionPhase.FAILED);
  }

  @Test
  void endsFailureWhenTheSessionsLogGrewAfterIt() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState failed = reducer.signed(task, new SessionSign.TurnFailed("overloaded"), 1_000);

    TaskState read =
        reducer.signed(
            failed,
            new SessionSign.Read(Session.Window.State.UNREAD, 0, 2_000, 0, 5_000, null),
            3_000);

    assertThat(SessionPhase.of(read.work().session())).isEqualTo(SessionPhase.WORKING);
  }

  @Test
  void endsTheSessionWithWhatItPrintedWhenReadFindsItsWindowHoldingNoAgent() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();

    TaskState read =
        reducer.signed(
            task,
            new SessionSign.Read(
                Session.Window.State.DEAD_SHELL, 0, 0, 0, 5_000, "Error: bad config"),
            9_000);

    assertThat(SessionPhase.of(read.work().session())).isEqualTo(SessionPhase.GONE);
    assertThat(Objects.requireNonNull(read.work().session().halt()).cause())
        .isEqualTo("Error: bad config");
  }

  @Test
  void keepsTheCauseAnEndedSessionGaveWhenReadFindsItsWindowGone() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState ended = reducer.signed(task, new SessionSign.Ended("boom"), 8_000);

    TaskState read =
        reducer.signed(
            ended, new SessionSign.Read(Session.Window.State.MISSING, 0, 0, 0, 5_000, null), 9_000);

    assertThat(read.work().session().halt()).isEqualTo(ended.work().session().halt());
  }
}
