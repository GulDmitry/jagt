package dev.jagt.orchestrator.port.worktree;

import java.nio.file.Path;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The behaviour a repository records as specs, and the change a task adds to them. A repository
 * keeping none owes nothing and folds nothing. {@code root} is the specs folder relative to the
 * repository; null is where the spec tool looks by default. A change is the task's whatever the
 * case of its name.
 */
public interface Specs {

  /**
   * Why the task's change in this worktree may not be handed back yet, as the session is to read
   * it.
   */
  Optional<String> owed(Path worktree, @Nullable String root, String taskId);

  /** Folds the task's open changes into the repository's specs, throwing what refused it. */
  void fold(Path worktree, @Nullable String root, String taskId);
}
