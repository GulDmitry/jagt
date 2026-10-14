package dev.jagt.orchestrator.task;

import java.util.Locale;

/** What a guard refused a task. */
public enum Guard {

    /** A shell line the session was about to run. */
    SESSION_LINE,
    /** A status the task reported that its own status forbids. */
    STATUS_REPORT;

    public String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
