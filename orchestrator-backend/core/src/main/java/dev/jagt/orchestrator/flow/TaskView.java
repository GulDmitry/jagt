package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.flow.Phase;
import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.flow.DashboardLine;
import dev.jagt.orchestrator.flow.RoundState;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;


import java.util.List;
import java.util.Map;

/**
 * One task as a human surface sees it.
 *
 * <p>ONE projection for every front-end, so a status can never mean two different things in two places.
 * Serialized straight to JSON, which is why the actions carry their wire ids and labels rather than an enum name.
 */
public record TaskView(
        String id,
        String alias,
        String project,
        String title,
        TaskStatus status,
        // The same status in words a human needs no glossary for; the enum name stays the wire value.
        String statusLabel,
        Phase phase,
        Owner owner,
        // How loudly it asks: what the header counts and what the own-move filter keeps.
        Attention attention,
        // WHICH act is wanted, short enough for a chip; null exactly when the tier above is NONE.
        String ask,
        String hint,
        List<ActionView> actions,
        String primaryAction,
        String detail,
        String ticketUrl,
        String reviewRequestUrl,
        // A single-repo task has one entry, so nothing needs a second shape for the ordinary case.
        List<RepoView> repos,
        long lastActiveAt,
        // Since when it has been in THIS status — not the activity stamp a keep-alive bumps.
        long statusSince,
        // When the code host says the review request was opened; 0 = no request, or no read has said yet.
        long requestOpenedAt,
        List<StatusChange> history,
        // Whether its code is on a shared branch RIGHT NOW: a deploy puts it there whatever status follows, and
        // only a revert takes it back off.
        boolean deployed,
        // Drafted review replies are waiting in the worktree, and nothing else announces them.
        boolean draftedReplies,
        // What the last round REPORTED, which no status carries: all three of its outcomes end at REVIEW_PENDING,
        // and a round that changed nothing left nothing behind to ship.
        AgentReport round,
        AutoReviewWatch autoReview,
        // `pipeline` is the verdict anything decides on; `pipelineSaid` is the host's own wording, for display.
        Pipeline pipeline,
        String pipelineSaid,
        // Whether the request is approved; null until a read has said. A status cannot answer this: the wait for
        // an approval starts the moment the request opens, and only one status is ever the approval itself.
        Boolean approved,
        long tokens
) {

    public record ActionView(String id, String label, String hint, boolean primary, String group,
                            boolean readOnly) {
    }

    /**
     * The deploy branch is carried per repository so a surface asking a human to confirm a shared-branch write can
     * say WHICH branch instead of "the deploy branch"; null when the project configures none.
     */
    public record RepoView(String project, String reviewRequestUrl, String deployBranch) {
    }

    public static TaskView of(String id, TaskState task, boolean draftedReplies, AutoReviewWatch autoReview,
                              Map<String, String> deployBranches) {
        Move move = Move.forTask(task.status(), task.hasReviewRequest(),
                RoundState.of(task.message(), draftedReplies), task.agentIsSilent(),
                autoReview == null ? AutoReviewWatch.none() : autoReview);
        List<ActionView> actions = move.actions().stream()
                .map(action -> new ActionView(action.id(), action.label(), action.hint(),
                        action == move.primary(), action.group().id(), action.readOnly()))
                .toList();
        return new TaskView(id, task.alias(), task.project(), task.title(), task.status(),
                task.status().label(), move.phase(),
                move.owner(), move.attention(), move.ask(), move.hint(), actions,
                move.primary() == null ? null : move.primary().id(),
                DashboardLine.forTask(task, webLink(task.mrUrl())), webLink(task.ticketUrl()),
                webLink(task.mrUrl()),
                task.repos().stream()
                        .map(repo -> new RepoView(repo.project(), webLink(repo.mrUrl()),
                                deployBranches.get(repo.project())))
                        .toList(),
                task.lastActiveTimestamp(),
                task.statusSince(), task.hasReviewRequest() ? task.requestOpenedAt() : 0,
                task.history(), deployed(task), draftedReplies, AgentReport.of(task.message()), autoReview,
                Pipeline.of(task.pipelineStatus()), task.pipelineStatus(),
                task.hasReviewRequest() ? task.approved() : null,
                task.totalUsage().total());
    }

    /** The merge commit each repository still holds IS the answer: a revert forgets it as it takes the work out. */
    private static boolean deployed(TaskState task) {
        return task.repos().stream()
                .anyMatch(repo -> repo.deployCommit() != null && !repo.deployCommit().isBlank());
    }

    /**
     * A link a page can put in an {@code href}, or nothing. Neither URL is jagt's own — a model read, an agent's
     * status message, a hand-edited {@code state.json} — and the page that renders them can POST, so a
     * {@code javascript:} or {@code data:} URL would run there. Anything but http(s) is dropped rather than
     * escaped: there is nothing useful to show.
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
