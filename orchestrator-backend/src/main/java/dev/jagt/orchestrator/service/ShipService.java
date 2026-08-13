package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.codehost.CodeHost;
import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.model.MergeRequestRef;
import dev.jagt.orchestrator.model.MergeRequestSpec;
import dev.jagt.orchestrator.model.Move;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.ReviewRequestTitle;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code ship} — the human's approval, executed. Everything about it is mechanical: commit what is in the
 * worktree with a title jagt already owns, push the task branch, open or update the review request. It used to
 * be five steps of prose relayed to the agent, which bought three failure modes: the permission classifier
 * could stall {@code git commit} in a window nobody was watching, the title came back reworded, and the
 * report of the request's URL simply might not happen (hence a defensive "CI_POLLING requires the link").
 *
 * <p>With a {@link CodeHost} configured for the repository, none of that involves a model: SHIPPING stops
 * being a state a task can hang in, because there is no longer anyone to wait for. Without one, the old relay
 * is kept verbatim — opening a review request needs an API jagt does not otherwise have.
 *
 * <p>What stays with the agent either way is the judgement work: the code, and the review replies it drafted.
 * Posting those needs the thread each one answers, which the sweep does not carry, so it remains a small
 * follow-up instruction — and never on the critical path, so a dead agent no longer blocks a ship.
 */
@Service
public class ShipService {

    private final StateService stateService;
    private final ConfigService configService;
    private final GitService gitService;
    private final TmuxService tmuxService;
    private final OrchestratorTools tools;
    private final List<CodeHost> codeHosts;
    /** One ship at a time per task: two clicks in a row would push and call the host twice for nothing. */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public ShipService(StateService stateService, ConfigService configService, GitService gitService,
                       TmuxService tmuxService, OrchestratorTools tools, List<CodeHost> codeHosts) {
        this.stateService = stateService;
        this.configService = configService;
        this.gitService = gitService;
        this.tmuxService = tmuxService;
        this.tools = tools;
        this.codeHosts = codeHosts;
    }

    public String ship(String taskIdOrAlias) {
        String taskId = stateService.canonicalTaskId(taskIdOrAlias);
        if (!inFlight.add(taskId)) {
            return "ship " + taskId + ": already running — wait for it to finish";
        }
        try {
            return shipExclusively(taskId);
        } finally {
            inFlight.remove(taskId);
        }
    }

    private String shipExclusively(String taskId) {
        TaskState task = stateService.task(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task " + taskId + " not found in state.json"));
        ConfigService.ConfigFile config = configService.load();
        boolean hasRequest = task.mrUrl() != null && !task.mrUrl().isBlank();
        requireShippable(taskId, task.status(), hasRequest, agentLive(config, taskId));

        ProjectConfig project = configService.project(task.project());
        String targetBranch = project.baseBranch() == null ? ""
                : project.baseBranch().replaceFirst("^origin/", "");
        String title = ReviewRequestTitle.expand(config.codeReview().mrTitlePatternOrDefault(), taskId,
                task.title());

        Optional<CodeHost> host = codeHosts.stream()
                .filter(candidate -> candidate.hostsRepository(task.remoteUrl()))
                .findFirst();
        if (host.isEmpty()) {
            return relayToAgent(taskId, task, config, title, targetBranch, !hasRequest);
        }
        return shipOverRest(taskId, task, config, host.get(), title, targetBranch, !hasRequest);
    }

    private String shipOverRest(String taskId, TaskState task, ConfigService.ConfigFile config, CodeHost host,
                                String title, String targetBranch, boolean firstShip) {
        Path projectPath = Path.of(configService.project(task.project()).path()).toAbsolutePath().normalize();
        Path worktree = Path.of(task.worktreePath());
        // A review round's commit cannot be described by the backend — it does not know what the agent fixed —
        // so it is mechanical and honest instead of invented. The first ship uses the pattern title.
        GitService.Commit commit = gitService.commitAll(projectPath, worktree,
                firstShip ? title : taskId + " address review comments");
        gitService.pushBranch(projectPath, worktree, taskId);

        MergeRequestRef request = host.createOrUpdateMergeRequest(new MergeRequestSpec(task.remoteUrl(),
                        taskId, targetBranch, title,
                        config.codeReview().mergeRequestDefaultsOrDefault().removeSourceBranchOrDefault(),
                        config.codeReview().mergeRequestDefaultsOrDefault().squashOrDefault()))
                .orElseThrow(() -> new IllegalStateException("Pushed branch " + taskId + ", but "
                        + host.displayName() + " would not open the review request — check"
                        + " orchestrator.code-host.token and open it by hand if this repeats."));

        // A ROUND, not just a status: recorded in history and re-arming the auto-review window even when the
        // status was already CI_POLLING, which is the case for every round after the first.
        stateService.updateTask(taskId, state -> state.withReviewRound(request.url()));
        return "ship " + taskId + ": " + describe(commit) + ", pushed, "
                + (request.created() ? "opened " : "updated ") + request.url()
                + " — status CI_POLLING" + relayDraftedReplies(taskId, worktree, config);
    }

    /**
     * No code host for this repository: the agent still has to do it, exactly as before. Kept verbatim rather
     * than "improved" — an unconfigured setup must behave as it always has.
     */
    private String relayToAgent(String taskId, TaskState task, ConfigService.ConfigFile config, String title,
                               String targetBranch, boolean firstShip) {
        tools.writeTaskContext(taskId, shipInstruction(firstShip, title, taskId, targetBranch,
                repliesStep(config)));
        // SHIPPING says "underway": the status only reaches CI_POLLING when the agent reports the link back.
        stateService.updateTask(taskId, state -> state.withStatus(TaskStatus.SHIPPING, "shipping"));
        return "ship " + taskId + ": approval relayed — agent will commit "
                + (firstShip ? "\"" + title + "\" and open the review request"
                        : "a concise review-fix message on the existing request")
                + ", push, post replies, then report CI_POLLING."
                + " (Configure orchestrator.code-host to have jagt do this itself, without a model.)";
    }

    /**
     * The agent's drafted replies, posted as a FOLLOW-UP — off the critical path on purpose, so a dead agent
     * can no longer block a ship the way it did when the whole sequence was its job.
     */
    private String relayDraftedReplies(String taskId, Path worktree, ConfigService.ConfigFile config) {
        if (!Files.isRegularFile(worktree.resolve("review_replies.md"))) {
            return "";
        }
        if (!config.codeReview().postReviewRepliesOrDefault()) {
            return "; review_replies.md is left for you to post (codeReview.postReviewReplies=false)";
        }
        tools.appendTaskContext(taskId, "The change is committed, pushed and the review request is up to date —"
                + " there is NOTHING to commit or push.\n" + repliesStep(config)
                + "Then set status REVIEW_PENDING only if you had to change code; otherwise leave the status"
                + " alone.");
        return "; asked the agent to post the drafted replies";
    }

    private boolean agentLive(ConfigService.ConfigFile config, String taskId) {
        String session = tmuxService.sessionName(config.viewer().tmuxSession());
        return tmuxService.taskWindowState(config.viewer().sharedView() ? session : session + "-" + taskId,
                taskId) == TmuxService.WindowState.AGENT_RUNNING;
    }

    private static void requireShippable(String taskId, TaskStatus status, boolean hasRequest,
                                         boolean agentLive) {
        if (Move.shippable(status, agentLive, hasRequest)) {
            return;
        }
        throw new IllegalStateException("ship: " + taskId + " is " + status
                + (status == TaskStatus.SHIPPING
                        ? " with its agent still shipping — a ship is in flight; `focus` to watch it."
                        : " — ship needs a task still in progress (IN_PROGRESS/REVIEW_PENDING) or an existing"
                                + " review request to ship another round onto; this one has neither."));
    }

    private static String describe(GitService.Commit commit) {
        return commit.created()
                ? "committed " + commit.changedFiles() + " file(s)"
                : "nothing new to commit";
    }

    /** What the agent is told to do with review_replies.md, honouring both codeReview switches. */
    static String repliesStep(ConfigService.ConfigFile config) {
        if (!config.codeReview().postReviewRepliesOrDefault()) {
            return "Do NOT post any replies — LEAVE review_replies.md untouched for the human to post.\n";
        }
        if (config.codeReview().reviewReplyAuthorsOrEmpty().isEmpty()) {
            return "If review_replies.md exists, post each drafted reply to its thread, then delete it.\n";
        }
        return "If review_replies.md exists, post drafted replies ONLY to threads whose comment author matches"
                + " (case-insensitive) any of: "
                + String.join(", ", config.codeReview().reviewReplyAuthorsOrEmpty())
                + ". Leave replies to OTHER authors as drafts (do NOT post them); delete only posted ones.\n";
    }

    /**
     * The ship instruction for the relay path. First ship: commit the exact pattern title and open the
     * request. Review round: a concise one-liner that LEADS with the task id — the identifier always comes
     * first, but the full ticket title is not repeated — and the existing request keeps its title.
     */
    static String shipInstruction(boolean firstShip, String title, String taskId, String targetBranch,
                                  String repliesStep) {
        String commitStep = firstShip
                ? "1. Commit ALL current changes with EXACTLY this message: \"" + title + "\".\n"
                : "1. Commit ALL current changes with a CONCISE one-line message that STARTS with \"" + taskId
                        + "\" followed by a short imperative summary (max ~10 words) of ONLY the changes you"
                        + " just made (e.g. \"" + taskId + " Guard null sort key, fix header toggle\").\n";
        String requestStep = firstShip
                ? "3. No review request exists yet — create one via your code-host MCP: source " + taskId
                        + " -> target " + targetBranch + ", title \"" + title + "\".\n"
                : "3. The review request already exists — do NOT create a new one or retitle it.\n";
        return "This IS the human approval to ship. Do NOT re-verify, do NOT ask — do it now.\n"
                + commitStep
                + "2. Push branch " + taskId + ".\n"
                + requestStep
                + "4. " + repliesStep
                + "5. Report back with update_agent_status CI_POLLING, message \"MR: <the request url>\".";
    }
}
