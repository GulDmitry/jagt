package dev.jagt.orchestrator.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.terminal.WindowStream;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.scenario.OpenedWindows;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionStreamsTest {

  private final WindowStream stream = mock(WindowStream.class);
  private final AgentSessions sessions = mock(AgentSessions.class);
  private final OpenedWindows opened = mock(OpenedWindows.class);
  private final WindowStream.Sink sink = mock(WindowStream.Sink.class);
  private final WindowStream.View view = mock(WindowStream.View.class);
  private final SessionStreams streams = new SessionStreams(stream, sessions, opened);

  @Test
  void viewsTasksWindowInTheSessionHoldingIt() {
    when(sessions.windowOf("a1")).thenReturn(new SessionWindow("jagt-abc-1", "ABC-1"));
    when(stream.open("jagt-abc-1", "ABC-1", 120, 40, sink)).thenReturn(Optional.of(view));

    assertThat(streams.ofTask("a1", 120, 40, sink)).contains(view);
  }

  @Test
  void viewsWindowJagtOpensInTheSessionItIsIn() {
    when(opened.windowOf("login")).thenReturn(new SessionWindow("jagt", "login"));
    when(stream.open("jagt", "login", 80, 24, sink)).thenReturn(Optional.of(view));

    assertThat(streams.ofWindow("login", 80, 24, sink)).contains(view);
  }

  @Test
  void viewsNothingWhereTheHostHoldsNoSuchWindow() {
    when(sessions.windowOf("ABC-1")).thenReturn(new SessionWindow("jagt", "ABC-1"));
    when(stream.open("jagt", "ABC-1", 80, 24, sink)).thenReturn(Optional.empty());

    assertThat(streams.ofTask("ABC-1", 80, 24, sink)).isEmpty();
  }
}
