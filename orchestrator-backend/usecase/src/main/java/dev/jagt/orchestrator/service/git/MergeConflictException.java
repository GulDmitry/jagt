package dev.jagt.orchestrator.service.git;

import java.nio.file.Path;

/**
 * A deploy merge hit conflicts. The checkout is LEFT at {@link #deployWorktree} for the human to
 * resolve and deploy again.
 */
public class MergeConflictException extends IllegalStateException {

  private static final long serialVersionUID = 1L;
  private final transient Path deployWorktree;

  public MergeConflictException(
      String sourceBranch, String targetBranch, String details, Path deployWorktree) {
    super(
        "Merge CONFLICT merging "
            + sourceBranch
            + " into "
            + targetBranch
            + " — nothing was pushed."
            + " Resolve it in the deploy worktree "
            + deployWorktree
            + " (this is the "
            + targetBranch
            + " side; the "
            + sourceBranch
            + " branch and its review request are untouched): fix"
            + " the conflicts, `git add` them, then deploy again to finish. Details: "
            + details);
    this.deployWorktree = deployWorktree;
  }

  public Path deployWorktree() {
    return deployWorktree;
  }
}
