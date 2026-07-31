package dev.jagt.orchestrator.platform;

import java.nio.file.Path;

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
}
