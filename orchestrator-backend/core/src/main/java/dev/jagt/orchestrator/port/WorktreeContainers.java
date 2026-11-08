package dev.jagt.orchestrator.port;

import java.nio.file.Path;

/**
 * Whatever container stack was started from inside a directory jagt is about to delete, which keeps
 * running and holding its volumes once the directory is gone. Best-effort by contract: a host that
 * cannot answer must never stop a worktree from being removed.
 */
@FunctionalInterface
public interface WorktreeContainers {

  void stop(Path worktree);
}
