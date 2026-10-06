package dev.jagt.orchestrator.port;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The behaviour a repository records as specs, and the change a task adds to them. A repository keeping none
 * owes nothing and folds nothing.
 */
public interface Specs {

    /** Why the task's change in this worktree may not be handed back yet, as the session is to read it. */
    Optional<String> owed(Path worktree, String taskId);

    /** Folds the task's open changes into the repository's specs, throwing what refused it. */
    void fold(Path worktree, String taskId);
}
