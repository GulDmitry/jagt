package dev.jagt.orchestrator.task;

import java.util.List;

/**
 * The facts a launch needs about a work item.
 *
 * <p>Source-agnostic on purpose: the same facts come either from a tracker's own API
 * ({@link dev.jagt.orchestrator.port.Tracker}, free) or from a model read
 * ({@link dev.jagt.orchestrator.port.MasterAssistant}, paid), and no consumer may care which.
 *
 * @param exists         false = the item could not be read at all (gone, or the read failed)
 * @param key            the item's CANONICAL key, read back from the item and never parsed out of a URL — it
 *                       becomes a branch and a directory name
 * @param trackerProject the key of the project the item lives in
 * @param url            where a human opens it, or empty when the tracker exposes no such link
 */
public record TicketFacts(boolean exists, String key, String title, String trackerProject, List<String> labels,
                          String url) {

    /**
     * Whether the read came back with the two facts nothing downstream can be reconstructed without — the key
     * that names the work and the link a human opens. A reader that never reached the item reports the same
     * {@code exists=false} as an item that is genuinely gone, and an {@code exists=true} missing either of those
     * is that same non-answer in a shape a schema accepts, so a caller decides on this and never on
     * {@link #exists} alone. A title is NOT required: an item may honestly have none.
     */
    public boolean usable() {
        return exists && notBlank(key) && notBlank(url);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
