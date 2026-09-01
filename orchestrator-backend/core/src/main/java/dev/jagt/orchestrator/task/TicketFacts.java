package dev.jagt.orchestrator.task;

import java.util.List;

/**
 * The facts a launch needs about a work item. {@code exists=false} means the TRACKER says there is no such item; a
 * read that failed carries no facts at all, and no caller may merge the two. {@code key} is read back from the item
 * and never parsed out of a URL — it becomes a branch and a directory name.
 */
public record TicketFacts(boolean exists, String key, String title, String trackerProject, List<String> labels,
                          String url) {

    /**
     * Whether the read came back with everything an item that EXISTS must have. An {@code exists=true} missing any
     * of key, title or url is a non-answer in a shape a schema accepts, so a caller decides on this and never on
     * {@link #exists} alone.
     */
    public boolean usable() {
        return exists && notBlank(key) && notBlank(title) && notBlank(url);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
