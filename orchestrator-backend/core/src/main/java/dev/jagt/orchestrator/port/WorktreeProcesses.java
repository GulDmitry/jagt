package dev.jagt.orchestrator.port;

import java.nio.file.Path;

/**
 * Whatever is still running inside a directory jagt is about to delete — chiefly a language server, started
 * DETACHED by an editor plugin and holding a gigabyte or two. Best-effort by contract: a host that cannot answer
 * must never stop a worktree from being removed.
 */
public interface WorktreeProcesses {

    void reap(Path worktree);
}
