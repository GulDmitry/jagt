package dev.jagt.orchestrator.port.worktree;

import java.nio.file.Path;
import java.util.Map;

/**
 * Whatever container stack was started from inside a directory jagt is about to delete, which keeps
 * running and holding its volumes once the directory is gone. Best-effort by contract: a host that
 * cannot answer must never stop a worktree from being removed.
 */
public interface WorktreeContainers {

  /** What a process started in the worktree needs so its stack is the task's own, never shared. */
  Map<String, String> environment(Path worktree);

  void stop(Path worktree);
}
