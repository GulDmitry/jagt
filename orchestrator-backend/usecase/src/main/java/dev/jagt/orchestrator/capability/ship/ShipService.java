package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.task.ReviewRequestTitle;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code ship} — the human's approval, executed. The work is the agent's, in every repository the task holds; the
 * status only leaves SHIPPING when the agent reports the requests back.
 */
@Service
@RequiredArgsConstructor
public class ShipService {

    private final StateService stateService;
    private final ConfigService configService;
    private final AgentSessions sessions;
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
                .orElseThrow(() -> new IllegalArgumentException("Task " + taskId + " not found in state.json"));
        ConfigService.ConfigFile config = configService.load();
        String title = ReviewRequestTitle.expand(config.codeReview().mrTitlePatternOrDefault(), taskId,
                task.title());

        sessions.writeTaskContext(taskId, shipInstruction(title, taskId, targets(task), repliesStep(config)));
        return Outcome.relayed("ship " + taskId + ": relayed to the agent; SHIPPING until it reports the"
                + " request" + (task.repos().size() > 1 ? "s" : "")
                + (config.codeReview().postReviewRepliesOrDefault()
                        ? "" : "; review_replies.md is yours to post (postReviewReplies=false)"), "shipping");
    }

    /** {@code hasRequest} is asked of THAT repository: one of them can be a round behind after an unfinished ship. */
    public record Target(String project, String worktreePath, String targetBranch, boolean hasRequest) {
    }

    private List<Target> targets(TaskState task) {
        return task.repos().stream()
                .map(repo -> new Target(repo.project(), repo.worktreePath(), targetOf(task, repo),
                        repo.hasReviewRequest()))
                .toList();
    }

    /** A task cut from a parent feature branch merges back into it, not into that repository's configured base. */
    private String targetOf(TaskState task, TaskRepo repo) {
        String base = task.baseBranchOr(configService.project(repo.project()).baseBranch());
        return base == null ? "" : base.replaceFirst("^origin/", "");
    }

    static String repliesStep(ConfigService.ConfigFile config) {
        if (!config.codeReview().postReviewRepliesOrDefault()) {
            return "Do NOT post any replies — LEAVE review_replies.md untouched for the human to post.\n";
        }
        if (config.codeReview().reviewReplyAuthorsOrEmpty().isEmpty()) {
            return "If review_replies.md exists, post each drafted reply to its thread, then delete it."
                    + THREADS;
        }
        return "If review_replies.md exists, post drafted replies ONLY to threads whose comment author matches"
                + " (case-insensitive) any of: "
                + String.join(", ", config.codeReview().reviewReplyAuthorsOrEmpty())
                + ". Leave replies to OTHER authors as drafts (do NOT post them); delete only posted ones."
                + THREADS;
    }

    private static final String THREADS = " Resolve threads exactly as your `<review_replies>` rules say.\n";

    static String shipInstruction(String title, String taskId, List<Target> targets, String repliesStep) {
        return "This IS the human approval to ship. Do NOT re-verify, do NOT ask — do it now.\n"
                + (targets.size() > 1
                        ? "The task spans these repositories, and what each one still needs:\n" + listed(targets)
                                + "Do every step in EVERY one of them; a repository left behind is a"
                                + " half-shipped task.\n"
                        : "The task's repository, and what it still needs:\n" + listed(targets))
                + "1. Commit ALL current changes. Where the request is still to be opened the message is EXACTLY"
                + " \"" + title + "\"; where it is already open, a CONCISE one-line message that STARTS with \""
                + taskId + "\" followed by a short imperative summary (max ~10 words) of ONLY the changes you"
                + " just made (e.g. \"" + taskId + " Guard null sort key, fix header toggle\").\n"
                + "2. Push branch " + taskId + ".\n"
                + "3. Open the request each repository is missing via your code-host MCP: source " + taskId
                + " -> the target listed above, title \"" + title + "\" EXACTLY as given. Leave the description"
                + " empty, or one line for a decision the diff cannot show — never a report of what you did."
                + " Where one is already open, do NOT create another or retitle it.\n"
                + "4. " + repliesStep
                + "5. Report back with update_agent_status CI_POLLING and " + reportField(targets) + ".\n"
                + "This authorises ONE commit and ONE push PER REPOSITORY listed above, of what is in their"
                + " trees now — no fewer, and nothing after. It is single-use and does not clear this file"
                + " (rule 4).";
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
