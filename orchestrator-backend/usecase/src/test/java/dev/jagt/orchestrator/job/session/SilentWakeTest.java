package dev.jagt.orchestrator.job.session;

import static dev.jagt.orchestrator.job.TestTaskTurnsBuilder.taskTurns;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.session.SessionStanding;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.Step;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SilentWakeTest {

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final StepRelay relay = mock(StepRelay.class);
  private final TaskStops stops = mock(TaskStops.class);
  private final FakeClock clock = new FakeClock();

  @TempDir Path root;

  private StateService state;
  private TaskTurns turns;
  private SilentWake wake;

  @BeforeEach
  void taskWhoseSessionEndedItsTurnWithNoReportAndStillRuns() {
    state = stateService().withRoot(root).build();
    turns = taskTurns().build(state, clock);
    wake = new SilentWake(sessions, relay, stops, OrchestratorProperties.defaults(), clock);
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .session(
                Session.defaults()
                    .withHalt(new Session.Halt(Session.Halt.Kind.IDLE, 1_000, null))
                    .withQuietSince(2_000)
                    .withWindow(new Session.Window(Session.Window.State.RUNNING, 1, 0)))
            .build());
  }

  @Test
  void nudgesTheSessionOnce() {
    wake.wake("ABC-42", state.task("ABC-42").orElseThrow());

    verify(relay)
        .say(
            "ABC-42",
            Step.SILENT,
            SilentWake.NUDGE,
            SessionStanding.of(state.task("ABC-42").orElseThrow()));
    verify(stops, never()).stop(anyString(), any(TaskStop.Trigger.class), anyString());
  }

  @Test
  void handsItToFreshSessionOnceTheNudgeWasGivenItsThreshold() {
    state.updateTask(
        "ABC-42", task -> task.withSession(task.work().session().withQuietSince(clock.millis())));
    turns.opened("ABC-42", "silent", Turn.digestOf(SilentWake.NUDGE), TaskStatus.IN_PROGRESS);
    clock.advance(Duration.ofMinutes(5));

    wake.wake("ABC-42", state.task("ABC-42").orElseThrow());

    verify(relay)
        .relay(
            "ABC-42",
            Step.SILENT,
            SilentWake.RELAY,
            StepRelay.Delivery.UNDER_FRESH,
            SessionStanding.of(state.task("ABC-42").orElseThrow()));
    verify(stops, never()).stop(anyString(), any(TaskStop.Trigger.class), anyString());
  }

  @Test
  void nudgesAgainWhereTheNudgeAnsweredAnEarlierSilence() {
    turns.opened("ABC-42", "silent", Turn.digestOf(SilentWake.NUDGE), TaskStatus.IN_PROGRESS);
    clock.advance(Duration.ofMinutes(10));
    state.updateTask(
        "ABC-42", task -> task.withSession(task.work().session().withQuietSince(clock.millis())));

    wake.wake("ABC-42", state.task("ABC-42").orElseThrow());

    verify(relay)
        .say(
            "ABC-42",
            Step.SILENT,
            SilentWake.NUDGE,
            SessionStanding.of(state.task("ABC-42").orElseThrow()));
  }

  @Test
  void waitsForTheNudgeAnswerBeforeItsThresholdIsOver() {
    turns.opened("ABC-42", "silent", Turn.digestOf(SilentWake.NUDGE), TaskStatus.IN_PROGRESS);
    clock.advance(Duration.ofMinutes(4));

    wake.wake("ABC-42", state.task("ABC-42").orElseThrow());

    verify(relay, never())
        .relay(
            anyString(),
            any(Step.class),
            anyString(),
            any(StepRelay.Delivery.class),
            any(SessionStanding.class));
    verify(relay, never())
        .say(anyString(), any(Step.class), anyString(), any(SessionStanding.class));
  }

  @Test
  void stopsTaskForHumanOnceFreshSessionWasGivenItsThreshold() {
    turns.opened("ABC-42", "silent", Turn.digestOf(SilentWake.RELAY), TaskStatus.IN_PROGRESS);
    clock.advance(Duration.ofMinutes(5));

    wake.wake("ABC-42", state.task("ABC-42").orElseThrow());

    verify(stops).stop("ABC-42", TaskStop.Trigger.SILENT, SilentWake.EVIDENCE);
  }

  @Test
  void leavesTheTaskWhileTheFreshSessionHasItsThreshold() {
    turns.opened("ABC-42", "silent", Turn.digestOf(SilentWake.RELAY), TaskStatus.IN_PROGRESS);
    clock.advance(Duration.ofMinutes(4));

    wake.wake("ABC-42", state.task("ABC-42").orElseThrow());

    verify(stops, never()).stop(anyString(), any(TaskStop.Trigger.class), anyString());
  }

  @Test
  void doesNotTypeOverTheHumansDraft() {
    when(sessions.composing("ABC-42")).thenReturn(true);

    wake.wake("ABC-42", state.task("ABC-42").orElseThrow());

    verify(relay, never())
        .say(anyString(), any(Step.class), anyString(), any(SessionStanding.class));
  }

  @Test
  void leavesTheTaskAlreadyStoppedToTheHuman() {
    state.updateTask(
        "ABC-42", task -> task.withStop(new TaskStop(TaskStop.Trigger.SILENT, 3_000, "evidence")));

    wake.wake("ABC-42", state.task("ABC-42").orElseThrow());

    verify(relay, never())
        .say(anyString(), any(Step.class), anyString(), any(SessionStanding.class));
  }

  @Test
  void leavesSessionWhoseProcessDoesNotRunToTheHuman() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .session(
                Session.defaults()
                    .withHalt(new Session.Halt(Session.Halt.Kind.IDLE, 1_000, null))
                    .withQuietSince(2_000)
                    .withWindow(new Session.Window(Session.Window.State.DEAD_SHELL, 1, 0)))
            .build());

    wake.wake("ABC-42", state.task("ABC-42").orElseThrow());

    verify(relay, never())
        .say(anyString(), any(Step.class), anyString(), any(SessionStanding.class));
  }
}
