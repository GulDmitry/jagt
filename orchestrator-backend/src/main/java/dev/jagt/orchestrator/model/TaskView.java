package dev.jagt.orchestrator.model;

import java.util.List;

/**
 * One task as a human surface sees it: identity, where it is, whose turn it is, what may be done, and the
 * couple of facts worth showing (the review request, the ticket, what it has cost).
 *
 * <p>ONE projection for every front-end — the TUI, the plain-text {@code /status} and the web board all render
 * this, so a status can never mean two different things in two places. Serialized straight to JSON for the web
 * UI, which is why the actions carry their wire ids and labels rather than an enum name.
 */
public record TaskView(
        String id,
        String alias,
        String project,
        String title,
        TaskStatus status,
        Phase phase,
        Owner owner,
        String hint,
        List<ActionView> actions,
        String primaryAction,
        String detail,
        String ticketUrl,
        String reviewRequestUrl,
        long lastActiveAt,
        // Since when it has been in THIS status (not the activity stamp a keep-alive bumps) plus every step it
        // took, so a surface can say "waiting on you for 6h" and show the timeline instead of one word.
        long statusSince,
        List<StatusChange> history,
        // Drafted review replies are sitting in the worktree's review_replies.md. Nothing else announces them:
        // a human who does not know the convention ships a round and posts (or drops) replies they never read.
        boolean draftedReplies,
        long tokens
) {

    /** An offered action, ready for a button: what to POST back, what to write on it, why. */
    public record ActionView(String id, String label, String hint, boolean primary) {
    }

    public static TaskView of(String id, TaskState task, boolean draftedReplies) {
        Move move = Move.forTask(task.status(), task.mrUrl() != null && !task.mrUrl().isBlank());
        List<ActionView> actions = move.actions().stream()
                .map(action -> new ActionView(action.id(), action.label(), action.hint(),
                        action == move.primary()))
                .toList();
        return new TaskView(id, task.alias(), task.project(), task.title(), task.status(), move.phase(),
                move.owner(), move.hint(), actions,
                move.primary() == null ? null : move.primary().id(),
                DashboardLine.forTask(id, task), webLink(task.ticketUrl()), webLink(task.mrUrl()),
                task.lastActiveTimestamp(),
                task.statusSince(), task.history(), draftedReplies, task.usageOrNone().total());
    }

    /**
     * A link the board can put in an {@code href}, or nothing. Neither URL is jagt's own: the ticket link comes
     * back from a MODEL reading a tracker and the request link from an agent's status message, and both are
     * stored verbatim in a {@code state.json} the human may also hand-edit. The board renders them as clickable
     * anchors in a page that can POST {@code deploy} to the local API, so a {@code javascript:} or {@code data:}
     * URL arriving from any of those sources would run there. Guaranteeing it HERE covers every surface at once:
     * a link that is not http(s) is dropped, not escaped, because there is nothing useful to show.
     */
    private static String webLink(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.strip();
        boolean web = trimmed.regionMatches(true, 0, "http://", 0, 7)
                || trimmed.regionMatches(true, 0, "https://", 0, 8);
        return web ? trimmed : null;
    }
}
