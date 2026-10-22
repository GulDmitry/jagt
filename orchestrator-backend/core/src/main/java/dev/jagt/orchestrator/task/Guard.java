package dev.jagt.orchestrator.task;

import java.util.Locale;

/** What a guard refused a task. */
public enum Guard {

    /** A shell line the session was about to run. */
    SESSION_LINE,
    /** A status the task reported that its own status forbids. */
    STATUS_REPORT,
    /** Git config or a hook that changed since a human trusted them. */
    GIT_STATE,
    /** A write to a file the plan has not been approved for. */
    PLAN_FIRST;

    public String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
