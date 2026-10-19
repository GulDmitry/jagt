package dev.jagt.orchestrator.task;

import java.util.List;

/**
 * One review round: whether it is APPROVED, the latest pipeline result, and every discussion THREAD still awaiting
 * an answer — one entry per thread, its notes pre-formatted oldest first with the author of each, so a reviewer
 * answering a reply reads as the exchange it is. Plus {@code pipelineFailure}, what the host says went wrong when
 * that result is a failure. {@code exists=false} means the HOST says there is no such request — a read that failed
 * carries no facts at all, and no caller may merge the two. {@code openedAt} is the HOST's stamp, 0 when unknown;
 * jagt's own is never substituted, being wrong by days for a request a task was resumed on.
 */
public record ReviewFacts(boolean exists, boolean approved, String pipelineStatus, String pipelineFailure,
                          List<String> threads, long openedAt) {

    public ReviewFacts {
        pipelineFailure = pipelineFailure == null ? "" : pipelineFailure;
    }
}
