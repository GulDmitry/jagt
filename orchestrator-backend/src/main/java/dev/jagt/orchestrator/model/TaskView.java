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
        long tokens
) {

    /** An offered action, ready for a button: what to POST back, what to write on it, why. */
    public record ActionView(String id, String label, String hint, boolean primary) {
    }

    public static TaskView of(String id, TaskState task) {
        Move move = Move.forTask(task.status(), task.mrUrl() != null && !task.mrUrl().isBlank());
        List<ActionView> actions = move.actions().stream()
                .map(action -> new ActionView(action.id(), action.label(), action.hint(),
                        action == move.primary()))
                .toList();
        return new TaskView(id, task.alias(), task.project(), task.title(), task.status(), move.phase(),
                move.owner(), move.hint(), actions,
                move.primary() == null ? null : move.primary().id(),
                DashboardLine.forTask(id, task), task.ticketUrl(), task.mrUrl(), task.lastActiveTimestamp(),
                task.usageOrNone().total());
    }
}
