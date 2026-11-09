package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.port.SessionHost;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One window of an agent per project in a workspace: running, it is focused; else opened. Each
 * window's ending is read from the window, as a task's is, and logged once.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScenarioWindows {

  static final String PREFIX = "acceptance-";

  private final SessionHost sessions;
  private final ConfigService config;
  private final AgentRuntime agent;
  private final Map<String, Boolean> seenRunning = new ConcurrentHashMap<>();

  public String open(
      String project, String model, String prompt, CheckerReader.Workspace workspace) {
    String session = sessions.sessionName(config.load().viewer().session());
    String name = PREFIX + project;
    if (sessions.taskWindowState(session, name) == SessionHost.WindowState.AGENT_RUNNING
        && sessions.focusTaskWindow(session, session, name)) {
      log.atInfo().setMessage("acceptance session focused").addKeyValue("project", project).log();
      return "the acceptance session of " + project + " already runs: focused";
    }
    Path root = workspace.root();
    sessions.openShellWindow(
        session, session, name, root, agent.workspaceCommand(root, model, prompt, workspace));
    seenRunning.put(project, false);
    log.atInfo().setMessage("acceptance session opened").addKeyValue("project", project).log();
    return "acceptance session of " + project + " opened in " + root;
  }

  /** Logs the ending of every opened window that has ended, once, and stops watching it. */
  public void watch() {
    String session = sessions.sessionName(config.load().viewer().session());
    seenRunning.forEach((project, running) -> look(session, project, running));
  }

  private void look(String session, String project, boolean running) {
    String name = PREFIX + project;
    SessionHost.WindowState state;
    try {
      state = sessions.taskWindowState(session, name);
    } catch (SessionHost.WindowsUnreadable unread) {
      return;
    }
    if (state == SessionHost.WindowState.AGENT_RUNNING) {
      seenRunning.put(project, true);
    } else if (running || state == SessionHost.WindowState.DEAD_SHELL) {
      seenRunning.remove(project);
      ended(session, name, project, ending(state, running));
    }
  }

  private static String ending(SessionHost.WindowState state, boolean ranBefore) {
    if (state == SessionHost.WindowState.MISSING) {
      return "gone";
    }
    return ranBefore ? "finished" : "failed";
  }

  private void ended(String session, String name, String project, String ending) {
    var line =
        ("failed".equals(ending) ? log.atWarn() : log.atInfo())
            .setMessage("acceptance session ended")
            .addKeyValue("project", project)
            .addKeyValue("ending", ending);
    String printed = tail(session, name).orElse(null);
    if (printed != null) {
      line = line.addKeyValue("cause", printed);
    }
    line.log();
  }

  private Optional<String> tail(String session, String name) {
    try {
      return sessions.exitTail(session, name).map(OneLine::clipped).filter(t -> !t.isBlank());
    } catch (RuntimeException unread) {
      return Optional.empty();
    }
  }
}
