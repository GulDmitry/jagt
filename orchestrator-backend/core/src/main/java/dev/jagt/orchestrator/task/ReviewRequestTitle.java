package dev.jagt.orchestrator.task;

/** The title of a task's review request (and of its first commit), expanded from the configured pattern. */
public final class ReviewRequestTitle {

    private ReviewRequestTitle() {
    }

    /**
     * {@code {ticket}}/{@code {title}} filled in, with the id never appearing twice however often it runs. A task
     * that never got a title expands to the ticket alone.
     */
    public static String expand(String pattern, String taskId, String storedTitle) {
        String title = stripTicketPrefix(storedTitle, taskId);
        // With no title the placeholder's own separators go with it, wherever it sits in the pattern. Cleaning
        // up the RESULT instead would edit a title that legitimately ends in one of them.
        String filled = title == null || title.isBlank()
                ? pattern.replaceFirst("[\\s:|/–—-]*\\{title}[\\s:|/–—-]*", "")
                : pattern.replace("{title}", title);
        return filled.replace("{ticket}", taskId).trim();
    }

    /**
     * The title with a leading {@code <taskId>} (and its separators) removed, so applying the pattern can never
     * double the ticket. Idempotent: stripping an already-bare title is a no-op. Empty ("") when the title
     * carried nothing but the ticket; null stays null.
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
