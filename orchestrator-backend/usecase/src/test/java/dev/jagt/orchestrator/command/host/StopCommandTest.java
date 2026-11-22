package dev.jagt.orchestrator.command.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.stop.BoardStop;
import dev.jagt.orchestrator.task.TaskState;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StopCommandTest {

  private final BoardStop board = mock(BoardStop.class);
  private final StateService state = mock(StateService.class);
  private final AgentSessions sessions = mock(AgentSessions.class);

  @Test
  void interruptsEveryWorkingSessionAndStopsTheBoard() {
    Map<String, TaskState> tasks = new LinkedHashMap<>();
    tasks.put("ABC-1", null);
    tasks.put("ABC-2", null);
    tasks.put("ABC-3", null);
    when(state.tasks()).thenReturn(tasks);
    when(board.engage()).thenReturn(true);
    when(sessions.interrupt("ABC-1")).thenReturn(true);
    when(sessions.interrupt("ABC-2")).thenReturn(false);
    when(sessions.interrupt("ABC-3")).thenReturn(true);

    String answer = new StopCommand(board, state, sessions).run("");

    assertThat(answer)
        .isEqualTo("stopped: 2 session(s) interrupted, jobs and intake wait for `unstop`");
  }

  @Test
  void stopsTheBoardAndInterruptsTheRestWhereOneSessionCannotBeReached() {
    Map<String, TaskState> tasks = new LinkedHashMap<>();
    tasks.put("ABC-1", null);
    tasks.put("ABC-2", null);
    when(state.tasks()).thenReturn(tasks);
    when(board.engage()).thenReturn(true);
    when(sessions.interrupt("ABC-1")).thenThrow(new IllegalStateException("windows unreadable"));
    when(sessions.interrupt("ABC-2")).thenReturn(true);

    String answer = new StopCommand(board, state, sessions).run("");

    assertThat(answer).startsWith("stopped: 1 session(s) interrupted");
    verify(board).engage();
  }

  @Test
  void saysTheBoardAlreadyStoodStoppedWhereTheStopWasStanding() {
    when(state.tasks()).thenReturn(Map.of());
    when(board.engage()).thenReturn(false);

    String answer = new StopCommand(board, state, sessions).run("");

    assertThat(answer).startsWith("already stopped");
    verify(sessions, never()).interrupt(anyString());
  }

  @Test
  void asksOneQuestionAndIsOpenToTheMaster() {
    StopCommand command = new StopCommand(board, state, sessions);

    assertThat(command.confirmation()).isPresent();
    assertThat(command.whyNotTheArbiter()).isEmpty();
  }
}
