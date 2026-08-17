package dev.jagt.orchestrator.model;

import java.util.List;

/**
 * The facts a launch needs about a work item: what to name the task, what to call it on the board, and which
 * project it belongs to.
 *
 * <p>Deliberately tracker- and source-agnostic: the same facts come either from a tracker's own API
 * ({@link dev.jagt.orchestrator.tracker.Tracker}, free) or from a headless assistant read
 * ({@link dev.jagt.orchestrator.assistant.MasterAssistant}, paid), and no consumer may care which.
 *
 * @param exists         false = the item could not be read at all (gone, or the read failed)
 * @param key            the item's CANONICAL key, read back from the item and never parsed out of a URL — it
 *                       becomes a branch, a directory and a tmux window
 * @param trackerProject the key of the project the item lives in, matched against a jagt project's labels
 * @param url            where a human opens it, or empty when the tracker exposes no such link
 */
public record TicketFacts(boolean exists, String key, String title, String trackerProject, List<String> labels,
                          String url) {
}
