package dev.jagt.orchestrator.port.terminal;

import java.nio.file.Path;

/**
 * How a worktree is opened for the human's review checkpoint. Called on an explicit human request,
 * so unlike the other strategies it MAY throw; must not block beyond a launch timeout.
 */
public interface EditorDriver {

  /** True when no editor is configured: {@link #open} and {@link #openDiff} then only throw. */
  default boolean absent() {
    return false;
  }

  void open(Path path);

  /** Opens a diff/compare window between two paths, no project. */
  void openDiff(Path left, Path right);
}
