package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NudgeResubmitJobTest {

  @TempDir Path root;
  private final AgentSessions sessions = mock(AgentSessions.class);
  private StateService state;
  private TaskTurns turns;

  @BeforeEach
  void stateAndTurnsOverIt() {
    state = stateService().withRoot(root).build();
    turns =
        new TaskTurns(state, Clock.systemUTC(), new Checkers(new TaskChart(Composition.builtIn())));
  }

  @Test
  void marksTheTurnWhenItPressesEnterOnNudgeLeftInAnIdleInput() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    turns.opened("ABC-42", "round", "digest", TaskStatus.REVIEWED);
    when(sessions.resubmitUnsentNudge("ABC-42")).thenReturn(true);

    new NudgeResubmitJob(sessions, turns).runOn(state.tasks());

    assertThat(turns.of("ABC-42").mark(NudgeResubmitJob.MARK)).isEqualTo("1:1");
  }

  @Test
  void stopsPressingAfterFewTimesWithinOneTurn() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    turns.opened("ABC-42", "round", "digest", TaskStatus.REVIEWED);
    when(sessions.resubmitUnsentNudge("ABC-42")).thenReturn(true);
    NudgeResubmitJob job = new NudgeResubmitJob(sessions, turns);

    for (int tick = 0; tick < 8; tick++) {
      job.runOn(state.tasks());
    }

    verify(sessions, times(NudgeResubmitJob.CEILING)).resubmitUnsentNudge("ABC-42");
  }

  @Test
  void pressesNothingWhileTheTaskStandsStopped() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.PACE, 1, "evidence", null, null))
            .build());
    turns.opened("ABC-42", "round", "digest", TaskStatus.REVIEWED);
    when(sessions.resubmitUnsentNudge("ABC-42")).thenReturn(true);

    new NudgeResubmitJob(sessions, turns).runOn(state.tasks());

    verify(sessions, never()).resubmitUnsentNudge("ABC-42");
  }

  @Test
  void readsNoWindowOfTaskWhoseTurnTheSessionDoesNotHold() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    new NudgeResubmitJob(sessions, turns).runOn(state.tasks());

    verify(sessions, never()).resubmitUnsentNudge("ABC-42");
  }
}
