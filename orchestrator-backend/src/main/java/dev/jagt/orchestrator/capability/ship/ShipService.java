package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.GitService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.port.CodeHost;
import dev.jagt.orchestrator.task.MergeRequestRef;
import dev.jagt.orchestrator.task.MergeRequestSpec;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.task.ReviewRequestTitle;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code ship} — the human's approval, executed: commit the worktree under a title jagt owns, push the task
 * branch, open or update the review request.
 *
 * <p>With a {@link CodeHost} for the repository no model is involved, so SHIPPING stops being a state a task
 * can hang in. Without one the prose relay is kept VERBATIM: an unconfigured setup must behave as it always did.
 *
 * <p>Posting the drafted replies stays with the agent either way — a reply needs the thread it answers, which
 * the sweep does not carry — and never on the critical path, so a dead agent cannot block a ship.
 */
@Service
@RequiredArgsConstructor
public class ShipService {

    private final StateService stateService;
    private final ConfigService configService;
    private final GitService gitService;
    private final AgentSessions sessions;
    private final List<CodeHost> codeHosts;
    /** One ship at a time per task: two clicks in a row would push and call the host twice for nothing. */
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
        // ANY repository's request makes another round possible, which is the same question the projection asks
        // to OFFER the ship — the gate and the button must not answer it differently.
        boolean hasRequest = task.hasReviewRequest();

        String title = ReviewRequestTitle.expand(config.codeReview().mrTitlePatternOrDefault(), taskId,
                task.title());

        // Every repository must have a host of its own for the mechanical path: with one of them unhosted, half
        // the task would be pushed by jagt and half asked of the agent, and neither the human nor the agent
        // would know which half is which.
        Map<String, CodeHost> hosts = new LinkedHashMap<>();
        task.repos().forEach(repo -> codeHosts.stream()
                .filter(candidate -> candidate.hostsRepository(repo.remoteUrl()))
                .findFirst()
                .ifPresent(host -> hosts.put(repo.project(), host)));
        if (hosts.size() < task.repos().size()) {
            // The relay can only ask for ONE request and hear ONE link back (a status message carries one), so
            // for several repositories it would push some and lose the rest — the half-shipped state the sweep
            // then asks about forever. Refusing names what to configure instead.
            if (task.repos().size() > 1) {
                throw new IllegalStateException("ship " + taskId + ": "
                        + String.join(", ", unhosted(task, hosts.keySet())) + " has no code host, so jagt"
                        + " cannot open every request — set orchestrator.code-host, or ship by hand.");
            }
            return relayToAgent(taskId, task, config, title, targetOf(task, task.primary()), !hasRequest);
        }
        return shipOverRest(taskId, task, config, hosts, title);
    }

    private Outcome shipOverRest(String taskId, TaskState task, ConfigService.ConfigFile config,
                                Map<String, CodeHost> hosts, String title) {
        Map<String, String> requests = new LinkedHashMap<>();
        List<String> reported = new ArrayList<>();
        try {
            for (TaskRepo repo : task.repos()) {
                Shipped shipped = shipRepo(taskId, task, config, hosts.get(repo.project()), repo, title);
                requests.put(repo.project(), shipped.request().url());
                reported.add((task.repos().size() > 1 ? repo.project() + " " : "") + describe(shipped.commit())
                        + ", pushed, " + (shipped.request().created() ? "opened " : "updated ")
                        + shipped.request().url());
            }
        } catch (RuntimeException e) {
            // A repository that DID land has a pushed branch and an open request; losing that would make the
            // retry commit onto it as if it were a first ship, and leave the human a request jagt never named.
            if (!requests.isEmpty()) {
                stateService.updateTask(taskId, state -> state.withMrUrls(requests));
            }
            throw e;
        }
        // A ROUND, not just a status: recorded in history and re-arming the auto-review window even when the
        // status was already CI_POLLING, which is the case for every round after the first.
        stateService.updateTask(taskId, state -> state.withReviewRound(requests));
        String primary = requests.getOrDefault(task.primary().project(), requests.values().iterator().next());
        return Outcome.ok("ship " + taskId + ": " + String.join("; ", reported) + "; CI_POLLING"
                + relayDraftedReplies(taskId, Path.of(task.worktreePath()), config),
                "review request: " + primary);
    }

    /** What one repository's share of a ship produced. */
    private record Shipped(GitService.Commit commit, MergeRequestRef request) {
    }

    private static List<String> unhosted(TaskState task, Set<String> hosted) {
        return task.projects().stream().filter(project -> !hosted.contains(project)).toList();
    }

    /** One repository's share of a ship: its own commit, its own push, its own review request. */
    private Shipped shipRepo(String taskId, TaskState task, ConfigService.ConfigFile config, CodeHost host,
                             TaskRepo repo, String title) {
        Path projectPath = Path.of(configService.project(repo.project()).path()).toAbsolutePath().normalize();
        Path worktree = Path.of(repo.worktreePath());
        // A review round's commit cannot be described by the backend — it does not know what the agent fixed —
        // so it is mechanical and honest instead of invented. The first ship uses the pattern title, and which
        // ship this is, is asked of THIS repository: one of them can be a round behind after a failed ship.
        GitService.Commit commit = gitService.commitAll(projectPath, worktree,
                repo.hasReviewRequest() ? taskId + " address review comments" : title);
        gitService.pushBranch(projectPath, worktree, taskId);

        MergeRequestRef request = host.createOrUpdateMergeRequest(new MergeRequestSpec(repo.remoteUrl(),
                        taskId, targetOf(task, repo), title,
                        config.codeReview().mergeRequestDefaultsOrDefault().removeSourceBranchOrDefault(),
                        config.codeReview().mergeRequestDefaultsOrDefault().squashOrDefault()))
                .orElseThrow(() -> new IllegalStateException("Pushed branch " + taskId + ", but "
                        + host.displayName() + " would not open the review request — check"
                        + " orchestrator.code-host.token and open it by hand if this repeats."));
        return new Shipped(commit, request);
    }

    /**
     * What this repository's request merges into: the task's own base when the human named one at {@code do}
     * time — a task cut from a parent feature branch must merge back into it, not into the release branch —
     * otherwise that repository's own configured base, which is not the same branch in every repository.
     */
    private String targetOf(TaskState task, TaskRepo repo) {
        String base = task.baseBranchOr(configService.project(repo.project()).baseBranch());
        return base == null ? "" : base.replaceFirst("^origin/", "");
    }

    /**
     * No code host for this repository, so the agent is asked to push and open the request itself.
     */
    private Outcome relayToAgent(String taskId, TaskState task, ConfigService.ConfigFile config, String title,
                               String targetBranch, boolean firstShip) {
        sessions.writeTaskContext(taskId, shipInstruction(firstShip, title, taskId, targetBranch,
                repliesStep(config)));
        // Handed over, not done: the status only reaches CI_POLLING when the agent reports the link back.
        return Outcome.relayed("ship " + taskId + ": relayed to the agent (no code host); SHIPPING until it"
                + " reports the request", "shipping");
    }

    /**
     * The agent's drafted replies, posted as a FOLLOW-UP — off the critical path on purpose, so a dead agent
     * cannot block a ship.
     */
    private String relayDraftedReplies(String taskId, Path worktree, ConfigService.ConfigFile config) {
        if (!Files.isRegularFile(worktree.resolve("review_replies.md"))) {
            return "";
        }
        if (!config.codeReview().postReviewRepliesOrDefault()) {
            return "; review_replies.md is yours to post (postReviewReplies=false)";
        }
        sessions.appendTaskContext(taskId, "The change is committed, pushed and the review request is up to date —"
                + " there is NOTHING to commit or push.\n" + repliesStep(config)
                + "Then set status REVIEW_PENDING only if you had to change code; otherwise leave the status"
                + " alone.");
        return "; drafted replies relayed to the agent";
    }


    private static String describe(GitService.Commit commit) {
        return commit.created() ? commit.changedFiles() + " file(s)" : "no new commit";
    }

    /** What the agent is told to do with review_replies.md, honouring both codeReview switches. */
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

    /** A reply does not resolve a thread, and the next round relays every unresolved one. */
    private static final String THREADS = " Resolve a thread ONLY where you changed the code it asked for."
            + " Leave every thread you pushed back on or asked about UNRESOLVED — that disagreement is the"
            + " reviewer's to settle, and resolving it would read as agreement.\n";

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
                + "5. Report back with update_agent_status CI_POLLING, message \"review request: <the url>\".";
    }
}
