package dev.jagt.orchestrator.task;

/**
 * A review request that now exists on the host.
 *
 * @param url     the human-facing link
 * @param created true = this call opened it, false = it was already open and got updated
 */
public record MergeRequestRef(String url, boolean created) {
}
