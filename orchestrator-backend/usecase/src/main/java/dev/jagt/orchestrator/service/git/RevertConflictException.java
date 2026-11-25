package dev.jagt.orchestrator.service.git;

import java.nio.file.Path;

/**
 * A revert hit conflicts, or one left in its worktree is not resolved yet. The checkout is LEFT at
 * {@link #revertWorktree} for the human to resolve and revert again.
 */
public class RevertConflictException extends IllegalStateException {

  private static final long serialVersionUID = 1L;
  private final transient Path revertWorktree;

  public RevertConflictException(
      String commit, String targetBranch, String details, Path revertWorktree) {
    super(
        "Revert CONFLICT reverting "
            + Shas.shortSha(commit)
            + " on "
            + targetBranch
            + " — nothing was pushed."
            + " Resolve it in the revert worktree "
            + revertWorktree
            + ": fix the conflicts, `git add`"
            + " them, then revert again to finish. Details: "
            + details);
    this.revertWorktree = revertWorktree;
  }

  public Path revertWorktree() {
    return revertWorktree;
  }
}
