package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;


import java.util.List;
import java.util.Map;

/**
 * One task as a human surface sees it: ONE projection for every front-end. Serialized straight to JSON, which is
 * why the actions carry their wire ids and labels rather than an enum name.
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
        String detail,
        String ticketUrl,
        String reviewRequestUrl,
        // A single-repo task has one entry, so nothing needs a second shape for the ordinary case.
        List<RepoView> repos,
        // Since when it has been in THIS status — not the activity stamp a keep-alive bumps.
        long statusSince,
        // When the code host says the review request was opened; 0 = no request, or no read has said yet.
        long requestOpenedAt,
        List<StatusChange> history,
        // Whether its code is on a shared branch RIGHT NOW; only a revert takes it back off.
        boolean deployed,
        // Drafted review replies are waiting in the worktree, and nothing else announces them.
        boolean draftedReplies,
        // What the last round REPORTED, which no status carries: all three outcomes end at REVIEW_PENDING.
        AgentReport round,
        AutoReviewWatch autoReview,
        // `pipeline` is the verdict anything decides on; `pipelineSaid` is the host's own wording, for display.
        Pipeline pipeline,
        String pipelineSaid,
        // Whether the request is approved; null until a read has said. No status can answer this.
        Boolean approved,
        long tokens
) {

    /** {@code again} = this verb has already run and what it did is still live, so pressing it repeats it. */
    public record ActionView(String id, String label, String hint, boolean primary, String group,
                            boolean readOnly, boolean again) {
    }

    /** {@code deployBranch} is per repository so a confirm can name it; null when the project configures none. */
    public record RepoView(String project, String reviewRequestUrl, String deployBranch) {
    }

    public static TaskView of(String id, TaskState task, boolean draftedReplies, AutoReviewWatch autoReview,
                              Map<String, String> deployBranches) {
        Move move = Move.forTask(task.status(), task.hasReviewRequest(),
                RoundState.of(task.message(), draftedReplies), task.agentIsSilent(),
                autoReview == null ? AutoReviewWatch.none() : autoReview);
        boolean deployed = deployed(task);
        List<ActionView> actions = move.actions().stream()
                .map(action -> new ActionView(action.id(), action.label(), action.hint(),
                        action == move.primary(), action.group().id(), action.readOnly(),
                        deployed && action == TaskAction.DEPLOY))
                .toList();
        return new TaskView(id, task.alias(), task.project(), task.title(), task.status(),
                task.status().label(), move.phase(),
                move.owner(), move.attention(), move.ask(), move.hint(), actions,
                DashboardLine.forTask(task, webLink(task.mrUrl())), webLink(task.ticketUrl()),
                webLink(task.mrUrl()),
                task.repos().stream()
                        .map(repo -> new RepoView(repo.project(), webLink(repo.mrUrl()),
                                deployBranches.get(repo.project())))
                        .toList(),
                task.statusSince(), task.hasReviewRequest() ? task.requestOpenedAt() : 0,
                task.history(), deployed, draftedReplies, AgentReport.of(task.message()), autoReview,
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
     * A link a page can put in an {@code href}, or nothing. Neither URL is jagt's own, so a {@code javascript:} or
     * {@code data:} URL would run on the page that renders it; anything but http(s) is dropped, not escaped.
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
