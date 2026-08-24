package dev.jagt.orchestrator.task;

import java.util.List;

/**
 * The facts a launch needs about a work item.

 *
 * @param exists         false = the TRACKER says there is no such item. A read that failed carries no facts
 *                       at all (empty {@code Optional}) — no caller may merge the two
 * @param key            the item's CANONICAL key, read back from the item and never parsed out of a URL — it
 *                       becomes a branch and a directory name
 * @param trackerProject the key of the project the item lives in
 * @param url            where a human opens it, or empty when the tracker exposes no such link
 */
public record TicketFacts(boolean exists, String key, String title, String trackerProject, List<String> labels,
                          String url) {

    /**
     * Whether the read came back with everything an item that EXISTS must have: the key that names the work, the
     * link a human opens, and a title to tell it apart from the next one. A reader that never reached the item
     * reports the same {@code exists=false} as an item that is genuinely gone, and an {@code exists=true}
     * missing any of the three is that same non-answer in a shape a schema accepts — so a caller decides on
     * this and never on {@link #exists} alone. A source with no summary of its own is not the exception: a
     * reader that can read the item at all can name it in a few words.
     *
     * <p>A read that KNOWS it failed says so and comes back with no facts at all; this covers the one that does
     * not know.
     */
    public boolean usable() {
        return exists && notBlank(key) && notBlank(title) && notBlank(url);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
