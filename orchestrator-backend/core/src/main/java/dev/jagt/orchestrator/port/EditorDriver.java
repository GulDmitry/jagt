package dev.jagt.orchestrator.port;

import java.nio.file.Path;
import java.util.List;

/**
 * Editor/IDE strategy: how a worktree is opened for the human's review checkpoint.
 *
 * <p>Contract: called on the human's explicit request, so unlike the other strategies it MAY throw on failure.
 * Must not block beyond a launch timeout.
 */
public interface EditorDriver {

    void open(Path path);

    /** Opens a diff/compare window between two paths, no project. */
    void openDiff(Path left, Path right);

    /**
     * Forget a just-removed worktree in the editor's own project registry — dead entries otherwise pile up, one
     * per task. Called AFTER the worktree is gone. Best-effort, default no-op.
     */
    default void forgetProject(Path worktreePath) {
    }

    /**
     * Garbage-collect EVERY dead jagt-worktree entry from the editor's recent-projects list — not just one. The
     * targeted {@link #forgetProject} runs while the editor is live, so its on-disk prune is clobbered when the
     * editor next flushes its own in-memory list; run on a schedule, this one lands for good the first tick the
     * editor is closed. Scoped to jagt worktrees so real projects are never touched; "dead" = the directory no
     * longer exists. Best-effort, default no-op.
     */
    default void forgetDeadWorktrees(List<WorktreeLocation> locations) {
    }

    /**
     * Where a project's jagt worktrees live: siblings of the repo under {@code parentDir}, which must be absolute
     * and normalized.
     */
    record WorktreeLocation(Path parentDir, String projectKey) {
    }
}
