package dev.jagt.orchestrator.port;

import java.nio.file.Path;

/**
 * Whatever is still running inside a directory jagt is about to delete — chiefly a language server, which an
 * editor plugin starts DETACHED so that it survives the session that spawned it and holds a gigabyte or two.
 *
 * <p>Finding those processes is the machine's business, not the orchestrator's: it is a different tool on every
 * platform. Best-effort by contract — a host that cannot answer must never stop a worktree from being removed,
 * because that would turn `done` into a no-op.
 */
public interface WorktreeProcesses {

    void reap(Path worktree);
}
