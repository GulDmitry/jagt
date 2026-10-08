package dev.jagt.orchestrator.flow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.AutoReviewWatch;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;

import java.util.LinkedHashMap;
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
        // Keyed by action id for every verb asked before it is sent, offered on this card or not.
        Map<String, String> confirmations,
        String detail,
        DashboardLine.Kind detailKind,
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
        // Drafted review replies are waiting in the worktree; read on the record, so no page is sent it.
        @JsonIgnore boolean draftedReplies,
        AutoReviewWatch autoReview,
        // `pipeline` is the verdict anything decides on; `pipelineSaid` is the host's own wording, for display.
        Pipeline pipeline,
        String pipelineSaid,
        // Whether the newest round could not read them, so the verdict above is an older round's.
        boolean pipelineUnread,
        // Whether the request is approved; null until a read has said. No status can answer this.
        Boolean approved
) {

    /** {@code again} = this verb has already run and what it did is still live, so pressing it repeats it. */
    public record ActionView(String id, String label, String hint, boolean primary, String group,
                            boolean readOnly, boolean again) {
    }

    public record RepoView(String project, String reviewRequestUrl) {
    }

    public static TaskView of(String id, TaskState task, boolean draftedReplies, AutoReviewWatch autoReview,
                              Map<String, String> deployBranches) {
        return of(id, task, RoundState.of(task.message(), draftedReplies), autoReview, deployBranches);
    }

    public static TaskView of(String id, TaskState task, RoundState round, AutoReviewWatch autoReview,
                              Map<String, String> deployBranches) {
        boolean draftedReplies = round.draftedReplies();
        Move move = Move.forTask(task.status(), Facts.projected(task), round, task.agentIsSilent(),
                autoReview == null ? AutoReviewWatch.none() : autoReview);
        boolean deployed = task.hasLiveDeploy();
        DashboardLine.Line line = DashboardLine.forTask(task, webLink(task.mrUrl()));
        List<ActionView> actions = move.actions().stream()
                .map(action -> new ActionView(action.id(), action.label(), action.hint(),
                        action == move.primary(), action.group().id(), action.readOnly(),
                        deployed && action == TaskAction.DEPLOY))
                .toList();
        return new TaskView(id, task.alias(), task.project(), task.title(), task.status(),
                round.masterReading() ? "master review"
                        : task.status().label(), move.phase(),
                move.owner(), move.attention(), move.ask(), move.hint(), actions,
                confirmations(id, task, deployBranches), line.text(), line.kind(), webLink(task.ticketUrl()),
                webLink(task.mrUrl()),
                task.repos().stream()
                        .map(repo -> new RepoView(repo.project(), webLink(repo.mrUrl())))
                        .toList(),
                task.statusSince(), task.hasReviewRequest() ? task.requestOpenedAt() : 0,
                task.history(), deployed, draftedReplies, autoReview,
                Pipeline.of(task.pipelineStatus()), task.pipelineStatus(), task.pipelineUnread(),
                task.hasReviewRequest() ? task.approved() : null);
    }

    private static Map<String, String> confirmations(String id, TaskState task, Map<String, String> deployBranches) {
        List<String> targets = task.repos().stream()
                .map(repo -> {
                    String branch = deployBranches.get(repo.project());
                    return repo.project() + " → "
                            + (branch == null || branch.isBlank() ? "no deployBranch in jagt.yml" : branch);
                })
                .toList();
        Map<String, String> confirmations = new LinkedHashMap<>();
        for (TaskAction action : TaskAction.values()) {
            action.confirmation(id, targets).ifPresent(question -> confirmations.put(action.id(), question));
        }
        return confirmations;
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
