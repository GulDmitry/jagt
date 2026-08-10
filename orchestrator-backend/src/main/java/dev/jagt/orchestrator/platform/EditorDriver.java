package dev.jagt.orchestrator.platform;

import java.nio.file.Path;
import java.util.List;

/**
 * Editor/IDE strategy: how a worktree is opened for the human's review
 * checkpoint. The generic default {@link CliEditorDriver} runs any CLI
 * launcher from {@code orchestrator.editor-command} (idea, code, subl) —
 * implement this interface only when a launcher command is not enough.
 *
 * <p>Contract: called on the human's explicit {@code ide} command, so unlike
 * the other strategies it MAY throw on failure — the error is reported back
 * to the Master as the tool result. Must not block beyond a launch timeout.
 */
public interface EditorDriver {

    void open(Path path);

    /** Opens a diff/compare window between two paths (no project). IntelliJ: `idea diff`. */
    void openDiff(Path left, Path right);

    /**
     * Forget a just-removed worktree in the editor's own project registry, so a `done` task doesn't leave a
     * dead entry in the IDE's recent-projects list (they pile up — one per task). Best-effort, default no-op;
     * only editors with an external project list (JetBrains) implement it. Called AFTER the worktree is gone.
     */
    default void forgetProject(Path worktreePath) {
        // no-op: the generic editor has no external recent-projects list to prune.
    }

    /**
     * Garbage-collect EVERY dead jagt-worktree entry from the editor's recent-projects list — not just one.
     * The targeted {@link #forgetProject} runs at {@code done} while the IDE is live, so its on-disk prune is
     * clobbered when the IDE next flushes its in-memory list (on save/exit); the entry survives restarts. This
     * runs on a schedule instead: the moment the IDE is closed for one tick the prune lands and stays (the IDE
     * won't re-add a project it isn't opening). Scoped to jagt worktrees ({@link WorktreeLocation}) so real
     * projects are never touched; "dead" = the entry's directory no longer exists. Best-effort, default no-op.
     */
    default void forgetDeadWorktrees(List<WorktreeLocation> locations) {
        // no-op: the generic editor has no external recent-projects list to prune.
    }

    /**
     * Where a project's jagt worktrees live: siblings of the repo named {@code <taskId>-<projectKey>} or
     * {@code <taskId>-deploy} under {@code parentDir}. {@code parentDir} must be absolute + normalized.
     */
    record WorktreeLocation(Path parentDir, String projectKey) {
    }
}
