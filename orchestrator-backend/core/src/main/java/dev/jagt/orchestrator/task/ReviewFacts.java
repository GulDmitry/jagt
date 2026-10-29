package dev.jagt.orchestrator.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * One review round: whether it is APPROVED, the latest pipeline result, and every discussion THREAD still awaiting
 * the agent's answer — one entry per thread, its notes pre-formatted oldest first as {@code <author>: <body>}, so a
 * reviewer answering a reply reads as the exchange it is. {@code newestNotes} holds, per thread and in its order, the
 * host's id of that thread's newest note. {@code awaitingReviewer} holds the unresolved threads whose
 * newest note is the agent's own. Plus {@code pipelineFailure}, what the host says went wrong when that result is a
 * failure, or why the listing could not be got when it is unknown. {@code exists=false} means the HOST says there is
 * no such request — a read that failed carries no facts at all, and no caller may merge the two. {@code openedAt} is
 * the HOST's stamp, 0 when unknown; jagt's own is never substituted, being wrong by days for a request a task was
 * resumed on. {@code pipelineUrl} is the address of the newest pipeline, blank when there is none. {@code merged} is
 * the HOST's word that the request was merged, whatever way it was.
 */
public record ReviewFacts(boolean exists, boolean approved, String pipelineStatus, String pipelineFailure,
                          List<String> threads, long openedAt, List<String> awaitingReviewer,
                          List<String> newestNotes, String pipelineUrl, boolean merged) {

    public ReviewFacts {
        pipelineFailure = pipelineFailure == null ? "" : pipelineFailure;
        awaitingReviewer = awaitingReviewer == null ? List.of() : List.copyOf(awaitingReviewer);
        newestNotes = newestNotes == null ? List.of() : List.copyOf(newestNotes);
        pipelineUrl = pipelineUrl == null ? "" : pipelineUrl;
    }

    /** Equal for two reads of the same open threads with the same newest notes, whatever the copied text; empty where any id is unknown. */
    public String noteFingerprint() {
        if (newestNotes.size() != threads.size() || newestNotes.stream().anyMatch(String::isBlank)) {
            return "";
        }
        return IntStream.range(0, threads.size())
                .mapToObj(i -> threads.get(i).strip().lines().findFirst().orElse("")
                        + " " + newestNotes.get(i))
                .sorted().collect(Collectors.joining("\n"));
    }

    /** The same round with its checks left unsaid. */
    public ReviewFacts withoutChecks() {
        return new ReviewFacts(exists, approved, "", "", threads, openedAt, awaitingReviewer, newestNotes, "", merged);
    }

    /** The threads whose newest note is by {@code account} moved to {@code awaitingReviewer}; blank moves none. */
    public ReviewFacts answeredBy(String account) {
        List<String> open = new ArrayList<>();
        List<String> openNotes = new ArrayList<>();
        List<String> answered = new ArrayList<>(awaitingReviewer);
        for (int i = 0; i < threads.size(); i++) {
            if (newestAuthorIs(threads.get(i), account)) {
                answered.add(threads.get(i));
            } else {
                open.add(threads.get(i));
                openNotes.add(i < newestNotes.size() ? newestNotes.get(i) : "");
            }
        }
        return new ReviewFacts(exists, approved, pipelineStatus, pipelineFailure, open, openedAt, answered, openNotes, pipelineUrl, merged);
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
