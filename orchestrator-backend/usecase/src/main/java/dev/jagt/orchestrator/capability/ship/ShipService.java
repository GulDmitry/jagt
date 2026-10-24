package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.task.ReviewRequestTitle;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code ship} — the human's approval, executed. The work is the agent's, in every repository holding work or
 * already in review; the status only leaves SHIPPING when the agent reports the requests back.
 */
@Service
@RequiredArgsConstructor
public class ShipService {

    private final StateService stateService;
    private final ConfigService configService;
    private final StepRelay relay;
    private final WorktreeChanges changes;
    /** One ship at a time per task: two clicks in a row would relay the same instruction twice. */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public Outcome ship(String taskIdOrAlias) {
        String taskId = stateService.canonicalTaskId(taskIdOrAlias);
        if (!inFlight.add(taskId)) {
            return Outcome.nothing("ship " + taskId + ": already running — wait for it to finish");
        }
        try {
            return shipExclusively(taskId);
        } finally {
            inFlight.remove(taskId);
        }
    }

    private Outcome shipExclusively(String taskId) {
        TaskState task = stateService.task(taskId)
                .orElseThrow(() -> Refusal.noSuchTask(taskId));
        ConfigService.ConfigFile config = configService.load();
        String title = ReviewRequestTitle.expand(config.codeReview().mrTitlePatternOrDefault(), taskId,
                task.title());

        List<Target> targets = targets(task);
        List<Target> carrying = targets.stream().filter(this::carries).toList();
        if (carrying.isEmpty()) {
            return Outcome.nothing("ship " + taskId + ": nothing to ship — no repository of this task holds"
                    + " work");
        }
        List<String> idle = targets.stream().filter(target -> !carrying.contains(target))
                .map(Target::project).toList();
        if (!relay.write(taskId, "ship", shipFacts(title, taskId, carrying, repliesStep(config)))) {
            return Outcome.nothing("ship " + taskId + ": no case sends ship at " + task.status()
                    + ", so the session was not told; see misses.jsonl");
        }
        return Outcome.relayed("ship " + taskId + ": relayed to the agent; SHIPPING until it reports the"
                + " request" + (carrying.size() > 1 ? "s" : "")
                + (idle.isEmpty() ? "" : "; nothing to ship in " + String.join(", ", idle))
                + (config.codeReview().postReviewRepliesOrDefault()
                        ? "" : "; review_replies.md is yours to post (postReviewReplies=false)"), "shipping");
    }

    /** One already in review stays in whatever it holds: a later commit and the drafted replies both go there. */
    private boolean carries(Target target) {
        return target.hasRequest()
                || changes.holdsWork(target.project(), target.worktreePath(), target.targetBranch());
    }

    /** {@code hasRequest} is asked of THAT repository: one of them can be a round behind after an unfinished ship. */
    public record Target(String project, String worktreePath, String targetBranch, boolean hasRequest) {
    }

    private List<Target> targets(TaskState task) {
        return task.repos().stream()
                .map(repo -> new Target(Objects.requireNonNull(repo.project()), Objects.requireNonNull(repo.worktreePath()),
                        WorktreeChanges.targetBranch(task, configService.project(Objects.requireNonNull(repo.project())).baseBranch()),
                        repo.hasReviewRequest()))
                .toList();
    }

    static String repliesStep(ConfigService.ConfigFile config) {
        // A right withheld from the Master is withheld from the ship it asked for, not from every ship.
        if (OriginContext.current() == ActionOrigin.MASTER && !config.master().may(MasterRight.REPLY)) {
            return "Do NOT post any replies — this ship is the Master session's, which may not answer a"
                    + " person. LEAVE review_replies.md for the human.\n";
        }
        if (!config.codeReview().postReviewRepliesOrDefault()) {
            return "Do NOT post any replies — LEAVE review_replies.md untouched for the human to post.\n";
        }
        if (config.codeReview().reviewReplyAuthorsOrEmpty().isEmpty()) {
            return "If review_replies.md exists, post each drafted reply to its thread, then delete it."
                    + THREADS;
        }
        return "If review_replies.md exists, post a drafted reply ONLY where the note its block answers is by"
                + " an author matching (case-insensitive) any of: "
                + String.join(", ", config.codeReview().reviewReplyAuthorsOrEmpty())
                + ". Leave replies to OTHER authors as drafts (do NOT post them); delete only posted ones."
                + THREADS;
    }

    private static final String THREADS = " Resolve threads exactly as your `<review_replies>` rules say.\n";

    static String shipFacts(String title, String taskId, List<Target> targets, String repliesStep) {
        return "Ship " + taskId + ". Push branch " + taskId + ".\n"
                + "The request's title is EXACTLY \"" + title + "\"; a later round's commit message STARTS with \""
                + taskId + "\".\n"
                + (targets.size() > 1
                        ? "The task spans these repositories, and what each one still needs:\n" + listed(targets)
                                + "Do every step in EVERY one of them; a repository left behind is a"
                                + " half-shipped task.\n"
                        : "The task's repository, and what it still needs:\n" + listed(targets))
                + "Replies: " + repliesStep
                + "Report: update_agent_status CI_POLLING and " + reportField(targets) + ".";
    }

    private static String listed(List<Target> targets) {
        StringBuilder lines = new StringBuilder();
        targets.forEach(target -> lines.append("- ").append(target.project()).append(": ")
                .append(target.worktreePath()).append(", merges into ").append(target.targetBranch())
                .append(target.hasRequest() ? " — its request is already open" : " — NO request yet, open one")
                .append('\n'));
        return lines.toString();
    }

    /** One round however many requests it names, so every link is reported in ONE call. */
    private static String reportField(List<Target> targets) {
        if (targets.size() == 1) {
            return "reviewRequestUrl=<the url>";
        }
        return "reviewRequests={" + targets.stream()
                .map(target -> "\"" + target.project() + "\": \"<its url>\"")
                .collect(java.util.stream.Collectors.joining(", ")) + "}";
    }
}
