package dev.jagt.orchestrator.port;

import java.nio.file.Path;

/**
 * How a worktree is opened for the human's review checkpoint. Called on an explicit human request, so unlike the
 * other strategies it MAY throw; must not block beyond a launch timeout.
 */
public interface EditorDriver {

    void open(Path path);

    /** Opens a diff/compare window between two paths, no project. */
    void openDiff(Path left, Path right);
}
