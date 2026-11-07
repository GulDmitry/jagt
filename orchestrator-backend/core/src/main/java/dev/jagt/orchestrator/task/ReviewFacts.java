package dev.jagt.orchestrator.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One review round: whether it is APPROVED, the latest pipeline result, and every discussion THREAD still awaiting
 * the agent's answer — one entry per thread, its notes pre-formatted oldest first as {@code <author>: <body>}, so a
 * reviewer answering a reply reads as the exchange it is. {@code newestNotes} holds the host's ids of the newest
 * note of each unresolved thread awaiting the agent, set from the host's own read and never by the model. {@code awaitingReviewer} holds the unresolved threads whose
 * newest note is the agent's own. Plus {@code pipelineFailure}, what the host says went wrong when that result is a
 * failure, or why the listing could not be got when it is unknown. {@code exists=false} means the HOST says there is
 * no such request — a read that failed carries no facts at all, and no caller may merge the two. {@code openedAt} is
 * the HOST's stamp, 0 when unknown; jagt's own is never substituted, being wrong by days for a request a task was
 * resumed on. {@code pipelineUrl} is the address of the newest pipeline, blank when there is none. {@code merged} is
 * the HOST's word that the request was merged, whatever way it was. {@code closed} is its word that the request was
 * closed unmerged.
 */
public record ReviewFacts(boolean exists, boolean approved, String pipelineStatus, String pipelineFailure,
                          List<String> threads, long openedAt, List<String> awaitingReviewer,
                          List<String> newestNotes, String pipelineUrl, boolean merged, boolean closed) {

    public ReviewFacts {
        pipelineFailure = pipelineFailure == null ? "" : pipelineFailure;
        awaitingReviewer = awaitingReviewer == null ? List.of() : List.copyOf(awaitingReviewer);
        newestNotes = newestNotes == null ? List.of() : List.copyOf(newestNotes);
        pipelineUrl = pipelineUrl == null ? "" : pipelineUrl;
    }

    /** The same for two reads carrying the same host note ids, whatever the copied text; unknown where there is none or any is blank. */
    public RoundKey noteKey() {
        return RoundKey.of(newestNotes.stream().distinct().sorted().toList());
    }

    /** The same round with {@code ids}, the host's, as its newest notes. */
    public ReviewFacts withNewestNotes(List<String> ids) {
        return new ReviewFacts(exists, approved, pipelineStatus, pipelineFailure, threads, openedAt, awaitingReviewer, ids, pipelineUrl, merged, closed);
    }

    public String noteFingerprint() {
        return noteKey().stored();
    }

    /** The same round with its checks left unsaid. */
    public ReviewFacts withoutChecks() {
        return new ReviewFacts(exists, approved, "", "", threads, openedAt, awaitingReviewer, newestNotes, "", merged, closed);
    }

    /** The threads whose newest note is by {@code account} moved to {@code awaitingReviewer}; blank moves none. */
    public ReviewFacts answeredBy(String account) {
        List<String> open = new ArrayList<>();
        List<String> answered = new ArrayList<>(awaitingReviewer);
        for (int i = 0; i < threads.size(); i++) {
            if (newestAuthorIs(threads.get(i), account)) {
                answered.add(threads.get(i));
            } else {
                open.add(threads.get(i));
            }
        }
        return new ReviewFacts(exists, approved, pipelineStatus, pipelineFailure, open, openedAt, answered, newestNotes, pipelineUrl, merged, closed);
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
