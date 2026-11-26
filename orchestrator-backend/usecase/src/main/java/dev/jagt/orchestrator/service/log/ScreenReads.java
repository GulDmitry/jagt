package dev.jagt.orchestrator.service.log;

import dev.jagt.orchestrator.protocol.debug.ScreenRead;
import dev.jagt.orchestrator.service.AgentSessions;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** What a task's terminal shows now; nothing here sends a key. */
@Service
@RequiredArgsConstructor
public class ScreenReads {

  private final AgentSessions sessions;

  public String screen(ScreenRead request) {
    return sessions
        .screen(request.taskId())
        .map(shown -> lastLines(shown, request.count()))
        .orElse(request.taskId() + " has no screen");
  }

  private static String lastLines(String shown, int count) {
    List<String> lines = shown.lines().toList();
    return String.join("\n", lines.subList(Math.max(0, lines.size() - count), lines.size()));
  }
}
