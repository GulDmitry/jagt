package dev.jagt.orchestrator.task;

import java.util.List;

/**
 * One review round: whether it is APPROVED, the latest pipeline result, and every UNRESOLVED discussion note, each
 * pre-formatted as one line. {@code exists=false} means the HOST says there is no such request — a read that failed
 * carries no facts at all, and no caller may merge the two. {@code openedAt} is the HOST's stamp, 0 when unknown;
 * jagt's own is never substituted, being wrong by days for a request a task was resumed on.
 */
public record ReviewFacts(boolean exists, boolean approved, String pipelineStatus, List<String> comments,
                          long openedAt) {

    /** A round from a source that cannot say when the request was opened. */
    public ReviewFacts(boolean exists, boolean approved, String pipelineStatus, List<String> comments) {
        this(exists, approved, pipelineStatus, comments, 0);
    }
}
