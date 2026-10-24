package dev.jagt.orchestrator.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One review round: whether it is APPROVED, the latest pipeline result, and every discussion THREAD still awaiting
 * the agent's answer — one entry per thread, its notes pre-formatted oldest first as {@code <author>: <body>}, so a
 * reviewer answering a reply reads as the exchange it is. {@code awaitingReviewer} holds the unresolved threads whose
 * newest note is the agent's own. Plus {@code pipelineFailure}, what the host says went wrong when that result is a
 * failure, or why the listing could not be got when it is unknown. {@code exists=false} means the HOST says there is
 * no such request — a read that failed carries no facts at all, and no caller may merge the two. {@code openedAt} is
 * the HOST's stamp, 0 when unknown; jagt's own is never substituted, being wrong by days for a request a task was
 * resumed on.
 */
public record ReviewFacts(boolean exists, boolean approved, String pipelineStatus, String pipelineFailure,
                          List<String> threads, long openedAt, List<String> awaitingReviewer) {

    public ReviewFacts {
        pipelineFailure = pipelineFailure == null ? "" : pipelineFailure;
        awaitingReviewer = awaitingReviewer == null ? List.of() : List.copyOf(awaitingReviewer);
    }

    /** The threads whose newest note is by {@code account} moved to {@code awaitingReviewer}; blank moves none. */
    public ReviewFacts answeredBy(String account) {
        List<String> open = new ArrayList<>();
        List<String> answered = new ArrayList<>(awaitingReviewer);
        threads.forEach(thread -> (newestAuthorIs(thread, account) ? answered : open).add(thread));
        return new ReviewFacts(exists, approved, pipelineStatus, pipelineFailure, open, openedAt, answered);
    }

    private static boolean newestAuthorIs(String thread, String account) {
        String who = normal(account);
        String newest = thread.strip().lines().reduce((first, second) -> second).orElse("");
        int colon = newest.indexOf(':');
        return !who.isEmpty() && colon > 0 && normal(newest.substring(0, colon)).equals(who);
    }

    private static String normal(String author) {
        String stripped = author.strip();
        return (stripped.startsWith("@") ? stripped.substring(1) : stripped).toLowerCase(Locale.ROOT);
    }
}
