package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.GitTrust;
import org.jspecify.annotations.Nullable;

/** The git region's states: nothing read yet, a state trusted, or a change found that waits for a human. */
public enum GitPhase {
    UNREAD, TRUSTED, CHANGED;

    public static GitPhase of(@Nullable GitTrust trust) {
        if (trust == null) {
            return UNREAD;
        }
        return trust.changed() == null ? TRUSTED : CHANGED;
    }
}
