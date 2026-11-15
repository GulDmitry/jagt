package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
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

  @Override
  public List<Problem> problems() {
    List<Problem> problems = new ArrayList<>();
    if (!Files.isDirectory(paths.root())) {
      problems.add(
          new Problem(
              "orchestrator.root",
              "orchestrator.root " + paths.root() + " is not a directory (ORCHESTRATOR_ROOT)."));
    }
    if (!Files.isDirectory(paths.data())) {
      problems.add(
          new Problem(
              DATA,
              DATA
                  + " "
                  + paths.data()
                  + " is not a directory and could not be created (ORCHESTRATOR_DATA)."));
    } else if (!Files.isWritable(paths.data())) {
      problems.add(
          new Problem(
              DATA, DATA + " " + paths.data() + " is not writable, so jagt could write nothing."));
    }
    Path stateDir = paths.stateFile().getParent();
    if (stateDir != null && !Files.isDirectory(stateDir)) {
      problems.add(
          new Problem(
              STATE_FILE,
              "orchestrator.state-file "
                  + paths.stateFile()
                  + ": "
                  + stateDir
                  + " is not a directory."));
    } else if (stateDir != null && !Files.isWritable(stateDir)) {
      problems.add(
          new Problem(
              STATE_FILE,
              "orchestrator.state-file "
                  + paths.stateFile()
                  + ": "
                  + stateDir
                  + " is not writable, so no task could ever be recorded."));
    } else if (Files.exists(paths.stateFile()) && !Files.isWritable(paths.stateFile())) {
      problems.add(
          new Problem(
              STATE_FILE, "orchestrator.state-file " + paths.stateFile() + " is not writable."));
    }
    return problems;
  }
}
