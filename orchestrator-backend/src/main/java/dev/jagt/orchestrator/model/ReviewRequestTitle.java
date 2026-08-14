package dev.jagt.orchestrator.model;

/**
 * The title of a task's review request (and of its first commit), expanded from {@code codeReview.mrTitlePattern}.
 * Lives here because two flows need the same answer — opening the request on {@code ship}, and storing a bare
 * title when {@code resume} inherits one that a pattern already prefixed.
 */
public final class ReviewRequestTitle {

    private ReviewRequestTitle() {
    }

    /**
     * {@code {ticket}}/{@code {title}} filled in, with the id never appearing twice however often it runs. A
     * task that never got a title expands to the ticket alone: it is the one field {@code do} cannot always
     * fill (an unreadable ticket, a `resume` before the request was read), and a request still has to open.
     */
    public static String expand(String pattern, String taskId, String storedTitle) {
        String title = stripTicketPrefix(storedTitle, taskId);
        return pattern.replace("{ticket}", taskId)
                .replace("{title}", title == null ? "" : title)
                .replaceFirst("[\\s:|/–—-]+$", "")
                .trim();
    }

    /**
     * The title with a leading {@code <taskId>} (and its separators) removed, so applying the pattern can
     * never double the ticket — a resumed task inherits the request's title, which the pattern already
     * prefixed. Idempotent: stripping an already-bare title is a no-op. Empty ("") when the title carried
     * nothing but the ticket; null stays null.
     */
    public static String stripTicketPrefix(String title, String taskId) {
        if (title == null) {
            return null;
        }
        String stripped = title.strip();
        if (taskId != null && !taskId.isBlank()
                && stripped.regionMatches(true, 0, taskId, 0, taskId.length())) {
            stripped = stripped.substring(taskId.length()).replaceFirst("^[\\s:|/–—-]+", "").strip();
        }
        return stripped;
    }
}
