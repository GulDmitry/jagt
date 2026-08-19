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
        Phase phase,
        Owner owner,
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
        List<StatusChange> history,
        // Drafted review replies are waiting in the worktree, and nothing else announces them.
        boolean draftedReplies,
        AutoReviewWatch autoReview,
        // `pipeline` is the verdict anything decides on; `pipelineSaid` is the host's own wording, for display.
        Pipeline pipeline,
        String pipelineSaid,
        long tokens
) {

    public record ActionView(String id, String label, String hint, boolean primary, String group) {
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
                RoundState.of(task.message(), draftedReplies), task.agentIsSilent());
        List<ActionView> actions = move.actions().stream()
                .map(action -> new ActionView(action.id(), action.label(), action.hint(),
                        action == move.primary(), action.group().id()))
                .toList();
        return new TaskView(id, task.alias(), task.project(), task.title(), task.status(), move.phase(),
                move.owner(), move.hint(), actions,
                move.primary() == null ? null : move.primary().id(),
                DashboardLine.forTask(task), webLink(task.ticketUrl()), webLink(task.mrUrl()),
                task.repos().stream()
                        .map(repo -> new RepoView(repo.project(), webLink(repo.mrUrl()),
                                deployBranches.get(repo.project())))
                        .toList(),
                task.lastActiveTimestamp(),
                task.statusSince(), task.history(), draftedReplies, autoReview,
                Pipeline.of(task.pipelineStatus()), task.pipelineStatus(),
                task.usageOrNone().total());
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
