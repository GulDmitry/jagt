package dev.jagt.orchestrator.task;

import java.util.Locale;

/** What a guard refused a task. */
public enum Guard {

    /** A shell line the session was about to run. */
    SESSION_LINE,
    /** A status the task reported that its own status forbids. */
    STATUS_REPORT,
    /** Git config or a hook that changed since a human trusted them. */
    GIT_STATE;

    public String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
