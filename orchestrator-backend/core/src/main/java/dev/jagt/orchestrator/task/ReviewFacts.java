package dev.jagt.orchestrator.task;

import java.util.List;

/**
 * One review round: whether it is APPROVED, the latest pipeline result, and every UNRESOLVED discussion note, each
 * pre-formatted as one line, plus {@code pipelineFailure}, what the host says went wrong when that result is a
 * failure. {@code exists=false} means the HOST says there is no such request — a read that failed
 * carries no facts at all, and no caller may merge the two. {@code openedAt} is the HOST's stamp, 0 when unknown;
 * jagt's own is never substituted, being wrong by days for a request a task was resumed on.
 */
public record ReviewFacts(boolean exists, boolean approved, String pipelineStatus, String pipelineFailure,
                          List<String> comments, long openedAt) {

    public ReviewFacts {
        pipelineFailure = pipelineFailure == null ? "" : pipelineFailure;
    }

    /** A round with no failure to read: its checks are not red, or the source cannot quote one. */
    public ReviewFacts(boolean exists, boolean approved, String pipelineStatus, List<String> comments,
                       long openedAt) {
        this(exists, approved, pipelineStatus, "", comments, openedAt);
    }

    /** A round from a source that cannot say when the request was opened. */
    public ReviewFacts(boolean exists, boolean approved, String pipelineStatus, List<String> comments) {
        this(exists, approved, pipelineStatus, "", comments, 0);
    }
}
