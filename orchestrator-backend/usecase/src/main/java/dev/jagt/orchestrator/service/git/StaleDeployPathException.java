package dev.jagt.orchestrator.service.git;

import java.nio.file.Path;
import java.util.List;

/**
 * The deploy worktree path of this repository is occupied by a checkout it did not cut, so nothing
 * was attempted. Typed so a caller landing SEVERAL repositories can come back to this one once the
 * path is free.
 */
public class StaleDeployPathException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  public StaleDeployPathException(Path deployWorktree, List<String> kept) {
    super(
        "The deploy worktree path "
            + deployWorktree
            + " is not a checkout but holds "
            + String.join(", ", kept)
            + ", so nothing can be deployed from there. Move or delete that"
            + " directory, then deploy again.");
  }
}
