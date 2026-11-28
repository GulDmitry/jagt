package dev.jagt.orchestrator.port.worktree;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Whatever container stack was started from inside a directory jagt is about to delete, which keeps
 * running and holding its volumes once the directory is gone. Best-effort by contract: a host that
 * cannot answer must never stop a worktree from being removed.
 */
public interface WorktreeContainers {

  /** What a process started in the worktree needs so its stack is the task's own, never shared. */
  Map<String, String> environment(Path worktree);

  /**
   * Writes, into the worktree, the commands to put first on a session's PATH, which run a stack as
   * the one {@link #writeStackName} wrote for the worktree they run in; empty when the host has
   * none.
   */
  Optional<Path> installCommands(Path worktree);

  /** Writes the name of the worktree's stack into it, for the commands to read. */
  void writeStackName(Path worktree);

  void stop(Path worktree);
}
