package dev.jagt.orchestrator.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.service.AgentSessions;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaskViewerTest {

  private final AgentSessions sessions = mock(AgentSessions.class);
  private final TerminalDriver terminal = mock(TerminalDriver.class);

  @ParameterizedTest
  @CsvSource({
    "WINDOW, and raised the agents window",
    "UNREACHABLE_TAB, a tab this terminal cannot select",
    "NOT_RUNNING, no agents viewer is open"
  })
  void saysWhatTheTerminalCouldActuallyDoAboutTheViewer(
      TerminalDriver.Revealed revealed, String expected) {
    when(sessions.focusTask("ABC-1")).thenReturn(new AgentSessions.Focused("ABC-1", "jagt", null));
    when(terminal.reveal("jagt")).thenReturn(revealed);

    assertThat(new TaskViewer(sessions, terminal).focus("ABC-1")).contains(expected);
  }

  @Test
  void saysTheSessionWasReEnteredWhenFocusFoundItDown() {
    when(sessions.focusTask("ABC-1"))
        .thenReturn(new AgentSessions.Focused("ABC-1", "jagt", "Claude"));
    when(terminal.reveal("jagt")).thenReturn(TerminalDriver.Revealed.WINDOW);

    assertThat(new TaskViewer(sessions, terminal).focus("ABC-1"))
        .contains("re-entered its Claude session");
  }

  @Test
  void closesTheAgentsViewerWhenNoTaskIsLeftAndItIsNotReserved() {
    when(sessions.viewerToClose()).thenReturn(Optional.of("jagt"));

    assertThat(new TaskViewer(sessions, terminal).closeIfNoTasksLeft()).isTrue();
    verify(terminal).closeViewerWindow("jagt");
  }

  @Test
  void leavesTheAgentsViewerOpenWhileItIsReserved() {
    when(sessions.viewerToClose()).thenReturn(Optional.empty());

    assertThat(new TaskViewer(sessions, terminal).closeIfNoTasksLeft()).isFalse();
  }
}
