package dev.jagt.orchestrator.model;

/**
 * A review request that now exists on the host.
 *
 * @param url     the human-facing link — the one the dashboard shows and the review sweep later reads
 * @param created true = this call opened it, false = it was already open and got updated. The caller needs the
 *                distinction: the first ship reports a new request to the human, a review round must not
 *                claim one.
 */
public record MergeRequestRef(String url, boolean created) {
}
