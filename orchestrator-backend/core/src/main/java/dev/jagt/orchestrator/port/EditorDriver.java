package dev.jagt.orchestrator.port;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * How a worktree is opened for the human's review checkpoint. Called on an explicit human request, so unlike the
 * other strategies it MAY throw; must not block beyond a launch timeout.
 */
public interface EditorDriver {

    void open(Path path);

    /** Opens a diff/compare window between two paths, no project. */
    void openDiff(Path left, Path right);

    /** Top-level names the editor writes into a directory of its own accord. */
    default Set<String> residue() {
        return Set.of();
    }

    /** Paths under a project root the editor keeps out of git that a worktree of the project needs as well. */
    default List<String> projectFiles() {
        return List.of();
    }

    /** Forgets a worktree that is already gone, in the editor's own project registry. Best-effort. */
    default void forgetProject(Path worktreePath) {
    }

    /**
     * Prunes every dead jagt-worktree entry from the editor's recent-projects list. A live editor clobbers an
     * on-disk prune when it next flushes its in-memory list, so this lands for good only while it is closed. Dead
     * means the directory no longer exists. Best-effort.
     */
    default void forgetDeadWorktrees(List<WorktreeLocation> locations) {
    }

    /** Where a project's jagt worktrees live: siblings of the repo under an absolute, normalized {@code parentDir}. */
    record WorktreeLocation(Path parentDir, String projectKey) {
    }
}
