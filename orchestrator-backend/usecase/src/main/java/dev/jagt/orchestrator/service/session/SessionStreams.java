package dev.jagt.orchestrator.service.session;

import dev.jagt.orchestrator.port.terminal.WindowStream;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.scenario.OpenedWindows;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The windows a human may watch and type into live: a task's session, and those jagt opens. */
@Service
@RequiredArgsConstructor
public class SessionStreams {

  private final WindowStream stream;
  private final AgentSessions sessions;
  private final OpenedWindows opened;

  public Optional<WindowStream.View> ofTask(
      String taskId, int columns, int rows, WindowStream.Sink sink) {
    return open(sessions.windowOf(taskId), columns, rows, sink);
  }

  public Optional<WindowStream.View> ofWindow(
      String name, int columns, int rows, WindowStream.Sink sink) {
    return open(opened.windowOf(name), columns, rows, sink);
  }

  private Optional<WindowStream.View> open(
      SessionWindow window, int columns, int rows, WindowStream.Sink sink) {
    return stream.open(window.session(), window.name(), columns, rows, sink);
  }
}
