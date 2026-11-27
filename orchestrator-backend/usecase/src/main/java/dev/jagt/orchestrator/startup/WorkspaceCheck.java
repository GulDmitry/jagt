package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The places jagt itself owns. A state file it cannot write is found on the first task, not here.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceCheck implements StartupCheck {

  private static final String DATA = "orchestrator.data";
  private static final String STATE_FILE = "orchestrator.state-file";

  private final OrchestratorPaths paths;
  private final AgentRuntime agentRuntime;

  @Override
  public List<Problem> problems() {
    List<Problem> problems = new ArrayList<>();
    if (!Files.isDirectory(paths.root())) {
      problems.add(
          new Problem(
              "orchestrator.root",
              "orchestrator.root " + paths.root() + " is not a directory (ORCHESTRATOR_ROOT)."));
    }
    if (!Files.isDirectory(paths.layout().data())) {
      problems.add(
          new Problem(
              DATA,
              DATA
                  + " "
                  + paths.layout().data()
                  + " is not a directory and could not be created (ORCHESTRATOR_DATA)."));
    } else if (!Files.isWritable(paths.layout().data())) {
      problems.add(
          new Problem(
              DATA,
              DATA
                  + " "
                  + paths.layout().data()
                  + " is not writable, so jagt could write nothing."));
    }
    Path stateDir = paths.layout().stateFile().getParent();
    if (stateDir != null && !Files.isDirectory(stateDir)) {
      problems.add(
          new Problem(
              STATE_FILE,
              "orchestrator.state-file "
                  + paths.layout().stateFile()
                  + ": "
                  + stateDir
                  + " is not a directory."));
    } else if (stateDir != null && !Files.isWritable(stateDir)) {
      problems.add(
          new Problem(
              STATE_FILE,
              "orchestrator.state-file "
                  + paths.layout().stateFile()
                  + ": "
                  + stateDir
                  + " is not writable, so no task could ever be recorded."));
    } else if (Files.exists(paths.layout().stateFile())
        && !Files.isWritable(paths.layout().stateFile())) {
      problems.add(
          new Problem(
              STATE_FILE,
              "orchestrator.state-file " + paths.layout().stateFile() + " is not writable."));
    }
    return problems;
  }

  /**
   * A data folder inside a checkout holding the agent's rule files: a session in a task worktree
   * there loads them, and the checkout's hooks, as the task's own.
   */
  @Override
  public List<Problem> notes() {
    Path data = paths.layout().data();
    for (Path dir = data.getParent(); dir != null; dir = dir.getParent()) {
      if (Files.exists(dir.resolve(".git")) && holdsAgentFile(dir)) {
        return List.of(
            new Problem(
                DATA,
                DATA
                    + " "
                    + data
                    + " lies inside the checkout "
                    + dir
                    + ", whose agent rule files every task session there loads; set "
                    + DATA
                    + " (ORCHESTRATOR_DATA) to a folder outside it."));
      }
    }
    return List.of();
  }

  private boolean holdsAgentFile(Path dir) {
    return agentRuntime.projectAgentFiles().stream()
        .anyMatch(name -> Files.exists(dir.resolve(name)));
  }
}
