package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.port.SessionHost;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** One window of an agent per project in a workspace: running, it is focused; else opened. */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScenarioWindows {

  static final String PREFIX = "acceptance-";

  private final SessionHost sessions;
  private final ConfigService config;
  private final AgentRuntime agent;

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
    log.atInfo().setMessage("acceptance session opened").addKeyValue("project", project).log();
    return "acceptance session of " + project + " opened in " + root;
  }
}
