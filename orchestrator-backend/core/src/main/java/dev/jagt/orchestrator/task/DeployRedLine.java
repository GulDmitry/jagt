package dev.jagt.orchestrator.task;

import org.jspecify.annotations.Nullable;

/** The line a deploy handed back for its pipeline carries, which says it was handed back for that. */
public final class DeployRedLine {

    public static final String CAUSE = "red pipeline on the deploy commit";

    private DeployRedLine() {
    }

    public static boolean marks(@Nullable String line) {
        return line != null && line.startsWith(CAUSE);
    }
}
