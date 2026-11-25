package dev.jagt.orchestrator.service.git;

import java.nio.file.Path;

public class ForeignDeployWorktreeException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  public ForeignDeployWorktreeException(Path deployWorktree, Path projectPath) {
    super(
        "The deploy worktree path "
            + deployWorktree
            + " holds a checkout "
            + projectPath.getFileName()
            + " did not cut, so its merge cannot be finished here. Deal with that checkout first"
            + " — finish its deploy, or `git worktree remove --force` it if it is stale.");
  }
}
