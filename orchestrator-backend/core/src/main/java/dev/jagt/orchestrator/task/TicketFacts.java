package dev.jagt.orchestrator.task;

import java.util.List;

/**
 * The facts a launch needs about a work item. {@code exists=false} means the TRACKER says there is no such item; a
 * read that failed carries no facts at all, and no caller may merge the two. {@code key} is read back from the item
 * and never parsed out of a URL — it becomes a branch and a directory name. {@code trackerStatus} and
 * {@code assignee} are what an intake trigger fires on, read back off the item itself rather than trusted from
 * whatever listed it.
 */
public record TicketFacts(boolean exists, String key, String title, String trackerProject, List<String> labels,
                          String url, String trackerStatus, String assignee) {

    public TicketFacts {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    /** An item nothing is known about yet: every caller starts here and names only the facts it holds. */
    public static TicketFacts defaults() {
        return new TicketFacts(false, "", "", "", List.of(), "", "", "");
    }

    /**
     * Whether the read came back with everything an item that EXISTS must have. An {@code exists=true} missing any
     * of key, title or url is a non-answer in a shape a schema accepts, so a caller decides on this and never on
     * {@link #exists} alone.
     */
    public boolean usable() {
        return exists && notBlank(key) && notBlank(title) && notBlank(url);
    }

    public TicketFacts withExists(boolean exists) {
        return new TicketFacts(exists, key, title, trackerProject, labels, url, trackerStatus, assignee);
    }

    public TicketFacts withKey(String key) {
        return new TicketFacts(exists, key, title, trackerProject, labels, url, trackerStatus, assignee);
    }

    public TicketFacts withTitle(String title) {
        return new TicketFacts(exists, key, title, trackerProject, labels, url, trackerStatus, assignee);
    }

    public TicketFacts withTrackerProject(String trackerProject) {
        return new TicketFacts(exists, key, title, trackerProject, labels, url, trackerStatus, assignee);
    }

    public TicketFacts withLabels(List<String> labels) {
        return new TicketFacts(exists, key, title, trackerProject, labels, url, trackerStatus, assignee);
    }

    public TicketFacts withUrl(String url) {
        return new TicketFacts(exists, key, title, trackerProject, labels, url, trackerStatus, assignee);
    }

    public TicketFacts withTrackerStatus(String trackerStatus) {
        return new TicketFacts(exists, key, title, trackerProject, labels, url, trackerStatus, assignee);
    }

    public TicketFacts withAssignee(String assignee) {
        return new TicketFacts(exists, key, title, trackerProject, labels, url, trackerStatus, assignee);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
