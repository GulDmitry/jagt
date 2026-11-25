package dev.jagt.orchestrator.adapter.terminal;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.worktree.WorktreeContainers;
import dev.jagt.orchestrator.service.git.WorktreeHooks;
import java.nio.file.Path;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The variables a task's session starts with, as shell assignments ahead of its command. */
@Component
@RequiredArgsConstructor
public class SessionEnvironment {

  private final OrchestratorPaths paths;
  private final WorktreeContainers containers;
  private final Host host;

  /** Where jagt's own session starts. */
  public Path root() {
    return paths.root();
  }

  /** A stable folder no worktree removal reaps. */
  public Path viewerDir() {
    return host.home();
  }

  public String prefix(Path worktree) {
    String stacks =
        containers.environment(worktree).entrySet().stream()
            .map(variable -> variable.getKey() + "=" + quoted(variable.getValue()) + " ")
            .collect(Collectors.joining());
    return WorktreeHooks.gitEnv(worktree) + stacks + outsideGit(worktree);
  }

  /**
   * A data folder may lie inside a repository: git must not climb out of a scratch folder to it.
   */
  private String outsideGit(Path worktree) {
    Path ceiling = paths.layout().scratchCeiling(worktree);
    return ceiling != null ? "GIT_CEILING_DIRECTORIES=" + quoted(ceiling.toString()) + " " : "";
  }

  private static String quoted(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }
}
