package dev.jagt.orchestrator.mcp;

import dev.jagt.orchestrator.agent.AgentRuntime;
import dev.jagt.orchestrator.agent.AgentWorktree;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.model.GitRemote;
import dev.jagt.orchestrator.model.Move;
import dev.jagt.orchestrator.model.ReviewRequestTitle;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.EditorDriver;
import dev.jagt.orchestrator.platform.TerminalDriver;
import dev.jagt.orchestrator.platform.UserNotifier;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.GitService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TmuxService;
import dev.jagt.orchestrator.service.WorktreeFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implements the MCP tools exposed to the Master and to the sub-agent sessions (whichever
 * {@link AgentRuntime} is active). The callerTaskId (resolved from the X-Working-Directory header) scopes
 * tool execution: a sub-agent may only act on its own task.
 */
@Service
public class OrchestratorTools {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorTools.class);
    /** Task ids become git branches, directory names and tmux window names/targets. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    private final ConfigService configService;
    private final StateService stateService;
    private final GitService gitService;
    private final TmuxService tmuxService;
    private final EditorDriver editorDriver;
    private final TerminalDriver terminalDriver;
    private final UserNotifier userNotifier;
    private final OrchestratorProperties properties;
    private final OrchestratorPaths paths;
    private final PromptTemplates prompts;
    private final AgentRuntime agentRuntime;
    /** Plugins the agent sessions should NOT load — heavy LSP plugins spawn a ~1-2GB JDT server per
     *  worktree and agents don't need them (they have file tools). Field-injected so the many test
     *  constructors need no change; null in tests = disable nothing. */
    @Value("${orchestrator.agent-disabled-plugins:}")
    private List<String> agentDisabledPlugins;

    public OrchestratorTools(ConfigService configService, StateService stateService, GitService gitService,
                             TmuxService tmuxService, EditorDriver editorDriver, TerminalDriver terminalDriver,
                             UserNotifier userNotifier, OrchestratorProperties properties, OrchestratorPaths paths,
                             PromptTemplates prompts, AgentRuntime agentRuntime) {
        this.agentRuntime = agentRuntime;
        this.configService = configService;
        this.stateService = stateService;
        this.gitService = gitService;
        this.tmuxService = tmuxService;
        this.editorDriver = editorDriver;
        this.terminalDriver = terminalDriver;
        this.userNotifier = userNotifier;
        this.properties = properties;
        this.paths = paths;
        this.prompts = prompts;
    }

    /** The configured project whose repo already has a branch named taskId, or null if none. */
    public String existingBranchProject(String taskId, String projectKey) {
        ConfigService.ConfigFile config = configService.load();
        Set<String> keys = projectKey != null && !projectKey.isBlank()
                ? Set.of(projectKey) : config.projects().keySet();
        return keys.stream()
                .filter(config.projects()::containsKey)
                .filter(k -> gitService.branchExists(
                        Path.of(config.projects().get(k).path()).toAbsolutePath().normalize(), taskId))
                .findFirst().orElse(null);
    }

    public String initializeTask(String taskId, String projectKey, String instructions, String mode,
                                 String branchStrategy, String title, String ticketUrl) {
        requireSafeId(taskId, "taskId");
        requireSafeId(projectKey, "projectKey");
        boolean plan = planMode(mode);
        GitService.BranchStrategy strategy = parseBranchStrategy(branchStrategy);
        if (stateService.task(taskId).isPresent()) {
            throw new IllegalArgumentException("Task " + taskId + " is already registered in state.json. "
                    + "Use open_task_tab to respawn its agent or remove_task to retire it first.");
        }
        ConfigService.ConfigFile config = configService.load();
        ProjectConfig project = config.projects().get(projectKey);
        if (project == null) {
            throw new IllegalArgumentException(
                    "Unknown project '" + projectKey + "'. Known projects: " + config.projects().keySet());
        }
        Path projectPath = Path.of(project.path()).toAbsolutePath().normalize();
        Path worktreePath = projectPath.getParent().resolve(taskId + "-" + projectKey);

        gitService.createWorktree(projectPath, worktreePath, taskId, project.baseBranch(), strategy);
        String remoteUrl;
        try {
            remoteUrl = gitService.remoteUrl(projectPath);
            WorktreeFiles.excludeOrchestratorPlumbing(gitService.gitCommonDir(projectPath));
            provisionForAgent(worktreePath);
            WorktreeFiles.copyIdeProjectFiles(projectPath, worktreePath);
            WorktreeFiles.copyLocalFiles(projectPath, worktreePath,
                    configService.load().worktree().copyGlobsOrDefault());
            WorktreeFiles.write(worktreePath.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE),
                    subAgentContext(taskId, projectKey, project, worktreePath, remoteUrl, config));
            if (instructions != null && !instructions.isBlank()) {
                WorktreeFiles.write(worktreePath.resolve("task_context.md"), instructions);
            }
        } catch (RuntimeException e) {
            // Compensate: without this, the taskId is burned (branch + worktree exist,
            // nothing registered) and a retry hits "branch already exists".
            gitService.removeWorktree(projectPath, worktreePath, taskId);
            throw e;
        }

        String alias = nextAlias(taskId);
        stateService.putTask(taskId, TaskState.builder(projectKey, worktreePath.toString(), TaskStatus.NEW)
                .lastActiveTimestamp(System.currentTimeMillis()).alias(alias).remoteUrl(remoteUrl).title(title)
                .ticketUrl(ticketUrl == null || ticketUrl.isBlank() ? null : ticketUrl)
                .autoReview(config.autoReview().enabledOrDefault())
                .build());

        String session;
        try {
            session = openTab(taskId, alias, worktreePath, config, plan);
        } catch (RuntimeException e) {
            return "Task " + taskId + " registered and worktree created at " + worktreePath
                    + ", but the agent session failed to start: " + e.getMessage()
                    + " Fix the cause and call open_task_tab(\"" + taskId + "\") — do NOT call initialize_task again.";
        }

        return "Task " + taskId + " initialized (alias: " + alias + ").\n"
                + "- worktree: " + worktreePath + " (branch " + taskId
                + (strategy == GitService.BranchStrategy.RESUME
                        ? ", RESUMED with its existing commits"
                        : " from " + project.baseBranch()) + ")\n"
                + "- " + agentRuntime.displayName() + " sub-agent started in tmux window '" + taskId
                + "' of session '" + session
                + "' (a Warp window attaches automatically; manual: tmux attach -t " + session + ")\n"
                + (plan
                        ? "- PLAN MODE: the agent plans first; the human approves the plan in its tmux window\n"
                        : "")
                + "- sub-agent context written to " + AgentRuntime.SYSTEM_KNOWLEDGE_FILE
                + (instructions != null && !instructions.isBlank() ? ", instructions to task_context.md" : "");
    }

    public String updateAgentStatus(String status, String message, String explicitTaskId, String callerTaskId) {
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status '" + status + "'. Allowed: "
                    + List.of(TaskStatus.values()));
        }
        String taskId = resolveTaskId(explicitTaskId, callerTaskId);
        String shortMessage = abbreviate(message);
        // The dashboard is the SSOT for "where is my MR" — a linkless CI_POLLING is a lie.
        if (newStatus == TaskStatus.CI_POLLING && (shortMessage == null || !shortMessage.contains("http"))) {
            throw new IllegalArgumentException(
                    "CI_POLLING requires the MR link in the message, e.g. \"MR: https://...\"");
        }
        TaskStatus previous = stateService.task(taskId).map(TaskState::status).orElse(null);
        String url = extractUrl(shortMessage);
        boolean updated = stateService.updateTask(taskId, t -> {
            TaskState next = t.withStatus(newStatus, shortMessage);
            if (url != null) {
                next = next.withMrUrl(url);
                // First time an MR is linked = the auto-review window start; never reset it on later rounds.
                if (t.mrCreatedAt() == 0) {
                    next = next.withMrCreatedAt(System.currentTimeMillis());
                }
            }
            return next;
        });
        if (!updated) {
            throw new IllegalArgumentException("Task " + taskId + " not found in state.json");
        }
        // Event-driven: the human doesn't poll, so ping them the moment a task hands
        // control back (finished review / broke CI). Only on the transition, never on
        // the agent's frequent IN_PROGRESS keep-alives.
        if (newStatus != previous && (newStatus == TaskStatus.REVIEW_PENDING || newStatus == TaskStatus.CI_FAILED)) {
            userNotifier.notify("jagt · " + taskId, Move.forTask(newStatus, true).hint());
        }
        return "Task " + taskId + " -> " + newStatus + (shortMessage == null ? "" : " (" + shortMessage + ")");
    }

    /**
     * Recovery path: the worktree and state entry exist, but the agent's tmux
     * window is gone (closed, crashed, or the agent died). Spawns a fresh one.
     */
    public String openTaskTab(String taskId, String mode) {
        taskId = canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        String session = openTab(taskId, task.alias(), Path.of(task.worktreePath()), configService.load(),
                planMode(mode));
        return "New " + agentRuntime.displayName() + " session started for " + taskId + " in tmux window '" + taskId + "' of session '"
                + session + "' (worktree " + task.worktreePath() + ")"
                + (planMode(mode) ? " in PLAN MODE" : "");
    }

    public String openInIde(String explicitTaskId, String mode, String callerTaskId) {
        String taskId = resolveTaskId(explicitTaskId, callerTaskId);
        TaskState task = requireTask(taskId);
        Path worktree = Path.of(task.worktreePath());
        if ("diff".equalsIgnoreCase(mode)) {
            // Explicit diff: a STATIC snapshot vs base — review-only, no project. Both sides are clean git
            // checkouts (a raw folder-diff of the live worktree would ignore .gitignore and dump build
            // artifacts). It does NOT auto-update: the right side is frozen at this call, so the IDE's
            // Refresh does nothing — re-run `ide <ticket> diff`, or use the live project (default) instead.
            ProjectConfig project = configService.project(task.project());
            Path projectPath = Path.of(project.path());
            // Diff against the DEPLOY branch (what the task merges into), not the base it was cut from — so
            // after a `deploy` conflict-merge you review only your own change vs dev, not all of dev's drift.
            // Falls back to baseBranch when the project has no deployBranch configured.
            String diffBase = project.deployBranch() != null && !project.deployBranch().isBlank()
                    ? "origin/" + project.deployBranch() : project.baseBranch();
            Path base = gitService.checkoutBaseForDiff(projectPath, diffBase, taskId);
            Path clean = gitService.checkoutWorktreeCleanForDiff(worktree, projectPath, diffBase, taskId);
            editorDriver.openDiff(base, clean);
            return "Opened STATIC diff of " + taskId + " (changes vs " + diffBase
                    + ") — snapshot, does not refresh; re-run for a fresh one";
        }
        if (mode != null && !mode.isBlank() && !"project".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Unknown ide mode '" + mode + "'. Allowed: project, diff");
        }
        // A DEPLOY_CONFLICT lives on the DEPLOY side, not in the task's own (clean) worktree — so `ide` opens
        // the deploy worktree, the ONLY place the merge can be resolved (fix the files, `git add`, then
        // `deploy` again). Opening the task worktree here would show nothing to resolve — the reported symptom.
        if (task.status() == TaskStatus.DEPLOY_CONFLICT) {
            Path deployWorktree = GitService.deployWorktreePath(Path.of(configService.project(task.project()).path()), taskId);
            if (Files.isDirectory(deployWorktree)) {
                editorDriver.open(deployWorktree);
                return "Opened the DEPLOY worktree " + deployWorktree + " — resolve the conflict there (fix the"
                        + " files, `git add` them), then `deploy " + taskId + "` again. Your task branch and its MR"
                        + " are untouched.";
            }
        }
        // Default: open the worktree as a project. Its live Git view (Local Changes) is the review surface —
        // auto-refreshing and .gitignore-aware, unlike the static `diff` snapshot.
        editorDriver.open(worktree);
        return "Opened " + task.worktreePath() + " as a project in the editor"
                + " (use Git → Local Changes for a live diff vs base)";
    }

    public String writeTaskContext(String taskId, String instructions) {
        taskId = canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        WorktreeFiles.write(Path.of(task.worktreePath()).resolve("task_context.md"), instructions);
        // A file on disk doesn't wake a running Claude session — nudge it directly.
        String session = agentSession(configService.load(), taskId);
        if (tmuxService.taskWindowState(session, taskId) == TmuxService.WindowState.AGENT_RUNNING
                && tmuxService.nudgeTaskWindow(session, taskId,
                        "The Master updated task_context.md — re-read it now and follow the new instructions.")) {
            return "Instructions written to task_context.md and the agent was nudged to re-read them.";
        }
        // Session not running (killed / crashed / never started): respawn it — a fresh Claude session
        // reads task_context.md on start and acts on the relayed instruction, so ship/review can't
        // dead-end against a dead agent.
        openTab(taskId, task.alias(), Path.of(task.worktreePath()), configService.load(), false);
        return "Instructions written to task_context.md; the agent session was down, so it was respawned"
                + " to read and follow them.";
    }

    /** Closes the task's tmux window(s), killing the Claude session; worktree and state stay. */
    public String closeTaskTab(String taskId, String callerTaskId) {
        taskId = resolveTaskId(taskId, callerTaskId);
        TaskState task = requireTask(taskId);
        int killed = tmuxService.killTaskWindows(
                agentSession(configService.load(), taskId), taskId);
        return killed == 0
                ? "No tmux window named '" + taskId + "' found — the session was already closed."
                : "Closed " + killed + " tmux window(s) for " + taskId + "; the " + agentRuntime.displayName() + " session is terminated. "
                        + "Worktree kept: " + task.worktreePath();
    }

    /** Retires a task: session killed, worktree and state entry removed; the branch survives. */
    public String removeTask(String taskId, String callerTaskId) {
        if (callerTaskId != null) {
            throw new IllegalArgumentException("remove_task is Master-only: a sub-agent cannot retire tasks");
        }
        taskId = canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        ConfigService.ConfigFile config = configService.load();
        // Kill the session first: removing a worktree under a live process's cwd
        // leaves a zombie agent grinding in a deleted directory.
        tmuxService.killTaskWindows(agentSession(config, taskId), taskId);
        ProjectConfig project = config.projects().get(task.project());
        if (project != null) {
            Path projectPath = Path.of(project.path());
            gitService.removeWorktree(projectPath, Path.of(task.worktreePath()), null);
            // An abandoned deploy conflict leaves a jagt-deploy-* worktree + branch — clear both so nothing
            // lingers on disk or as a dead IntelliJ project.
            gitService.removeDeployWorktreeIfPresent(projectPath, taskId);
            editorDriver.forgetProject(GitService.deployWorktreePath(projectPath, taskId));
        } else {
            log.warn("Project '{}' of task {} no longer in config.json; skipping worktree removal", task.project(), taskId);
        }
        // Drop the dead worktree from the IDE's recent-projects list so `done` tasks don't pile up there.
        editorDriver.forgetProject(Path.of(task.worktreePath()));
        stateService.removeTask(taskId);
        // Reserve the viewer by default: keep it open when empty so a manual
        // placement (dragged into a group/window) survives across task cycles.
        boolean closedViewer = stateService.tasks().isEmpty() && !config.viewer().keepViewerOrDefault();
        if (closedViewer) {
            terminalDriver.closeViewerWindow(tmuxService.sessionName(config.viewer().tmuxSession()));
        }
        return "Task " + taskId + " removed: worktree deleted, state entry dropped. Branch '" + taskId
                + "' was kept" + (project == null ? " (worktree left on disk: project missing from config.json)" : "")
                + (closedViewer ? ". Last task gone — the agents window was closed." : "");
    }

    /**
     * Re-enter a reopened task on its EXISTING branch (= taskId) plus a caller-supplied open MR, at
     * CI_POLLING — so `review`/`deploy` continue on that same MR instead of `ship` creating a new one.
     * The project is derived from the MR url (matched against each project's git remote). The branch
     * is resumed with its commits; the agent starts in review mode (does not re-implement).
     */
    public String resumeTask(String taskId, String mrUrl, String title) {
        if (mrUrl == null || !mrUrl.contains("http")) {
            throw new IllegalArgumentException("resume needs the MR url: resume <ticket> <mr-url>");
        }
        if (!SAFE_ID.matcher(taskId).matches()) {
            throw new IllegalArgumentException("Invalid ticket id '" + taskId + "'");
        }
        String projectKey = projectForMrUrl(mrUrl);
        String instructions = "Reopened for review. Your branch is resumed with its existing commits and MR "
                + mrUrl + " is open — there is NOTHING to build or commit right now. Do NOT re-implement, and"
                + " do NOT call update_agent_status: the Master has already set your status (CI_POLLING). Stay"
                + " idle; only when the Master relays review comments via task_context.md do you address them.";
        // The MR title the assistant read is already ticket-prefixed (the pattern built it); store it bare so
        // the dashboard isn't redundant and a later ship's pattern expansion stays single-prefixed.
        initializeTask(taskId, projectKey, instructions, null, "resume",
                ReviewRequestTitle.stripTicketPrefix(title, taskId), null);
        updateAgentStatus("CI_POLLING", "MR: " + mrUrl, taskId, null);
        return "Resumed " + taskId + " on its existing branch; linked MR " + mrUrl
                + "; status CI_POLLING — run `review` or `deploy`.";
    }

    private String projectForMrUrl(String mrUrl) {
        for (var e : configService.load().projects().entrySet()) {
            String path = GitRemote.projectPath(gitService.remoteUrl(Path.of(e.getValue().path())));
            if (path != null && mrUrl.contains(path)) {
                return e.getKey();
            }
        }
        throw new IllegalArgumentException("no configured project matches MR url: " + mrUrl);
    }

    /** Merges the task branch into the project's deploy branch and pushes it. */
    public String deployTask(String taskId, String callerTaskId) {
        if (callerTaskId != null) {
            throw new IllegalArgumentException("deploy_task is Master-only: a sub-agent cannot deploy");
        }
        taskId = canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        ProjectConfig project = configService.project(task.project());
        if (project.deployBranch() == null || project.deployBranch().isBlank()) {
            throw new IllegalArgumentException("Project '" + task.project()
                    + "' has no deployBranch in config.json — set it to enable deploy");
        }
        // HARD SAFETY: deploy is the ONLY merge in the whole system, and it must NEVER
        // target the base/release branch that tasks are cut from. jagt never writes there.
        String base = project.baseBranch() == null ? "" : project.baseBranch().replaceFirst("^origin/", "");
        if (project.deployBranch().equals(base)) {
            throw new IllegalArgumentException("REFUSED: deployBranch equals the base branch '" + base
                    + "'. jagt must never merge into the branch tasks are created from — point deployBranch"
                    + " at a downstream branch (e.g. dev).");
        }
        String deployBranch = project.deployBranch();
        try {
            gitService.mergeIntoAndPush(Path.of(project.path()), taskId, deployBranch);
        } catch (GitService.MergeConflictException e) {
            // Resolve on the DEPLOY side, never in the task branch: the MR targets the base branch, so merging
            // the deploy branch into the task branch would balloon its diff with everything the deploy branch
            // carries. jagt does NOT auto-open an editor — the dashboard flags DEPLOY_CONFLICT, the human opens
            // the worktree and resolves it, then deploys again (the backend does the push).
            stateService.updateTask(taskId,
                    t -> t.withStatus(TaskStatus.DEPLOY_CONFLICT, "resolve conflict in " + e.deployWorktree()));
            return "deploy " + taskId + ": MERGE CONFLICT merging into " + deployBranch + ". Open the deploy"
                    + " worktree yourself and resolve it: " + e.deployWorktree() + " — fix the conflicts,"
                    + " `git add` them, then `deploy " + taskId + "` again to finish. Your task branch and its"
                    + " MR are untouched.";
        }
        // The deploy worktree is gone once pushed; drop it from the editor's recent-projects list too, so a
        // human who opened it to resolve a conflict isn't left with a dead jagt-deploy entry.
        editorDriver.forgetProject(GitService.deployWorktreePath(Path.of(project.path()), taskId));
        // deploy IS a state transition — mark it so the dashboard's next move is 'done', not 'review'.
        stateService.updateTask(taskId, t -> t.withStatus(TaskStatus.DEPLOYED, "deployed to " + deployBranch));
        return "Merged branch " + taskId + " into " + deployBranch + " and pushed; status -> DEPLOYED";
    }

    public String listTasks() {
        return stateService.prettyJson();
    }

    /**
     * Task branches pile up forever: `done` keeps the branch by design (the work must survive a cleanup) and
     * nothing else removes one. This lists LOCAL branches whose work is already in the project's deployBranch
     * — deleting those loses nothing — and deletes them only when the human explicitly asks.
     *
     * <p>Scope, stated plainly because this deletes things: the candidates are ALL local merged branches, not
     * only the ones jagt created. jagt keeps no record of retired tasks, so it cannot tell its own leftovers
     * from a branch you merged by hand; the dry run IS the confirmation step, and it prints every name before
     * `prune all` touches anything. Never a remote branch (shared state — only `deploy` writes outward).
     */
    public String pruneBranches(boolean delete) {
        ConfigService.ConfigFile config = configService.load();
        // A task owns two branch names: its own, and the throwaway jagt-deploy-<task> a conflicted deploy
        // leaves behind (its tip equals the deploy branch, so git reports it as merged).
        Set<String> activeBranches = new HashSet<>();
        stateService.tasks().keySet().forEach(taskId -> {
            activeBranches.add(taskId);
            activeBranches.add("jagt-deploy-" + taskId);
        });
        StringBuilder out = new StringBuilder();
        int candidates = 0;
        int deleted = 0;
        int examined = 0;
        for (var entry : config.projects().entrySet()) {
            ProjectConfig project = entry.getValue();
            if (project.deployBranch() == null || project.deployBranch().isBlank()) {
                out.append(entry.getKey()).append(": no deployBranch configured — nothing to compare against\n");
                continue;
            }
            Path projectPath = Path.of(project.path()).toAbsolutePath().normalize();
            String into = "origin/" + project.deployBranch().replaceFirst("^origin/", "");
            List<String> prunable;
            try {
                prunable = prunable(gitService.branchesMergedInto(projectPath, into),
                        project.baseBranch(), project.deployBranch(), gitService.currentBranch(projectPath),
                        activeBranches);
            } catch (RuntimeException e) {
                // Keep going and keep the report: this is a multi-project sweep, and losing the record of
                // what was already deleted in project A because project B's remote ref is gone is worse
                // than the failure itself.
                out.append(entry.getKey()).append(": SKIPPED — ").append(e.getMessage()).append('\n');
                continue;
            }
            examined++;
            candidates += prunable.size();
            out.append(entry.getKey()).append(": ").append(prunable.size())
                    .append(" local branch(es) merged into ").append(into).append('\n');
            for (String branch : prunable) {
                if (!delete) {
                    out.append("  ").append(branch).append('\n');
                    continue;
                }
                var failure = gitService.deleteLocalBranch(projectPath, branch);
                if (failure.isEmpty()) {
                    deleted++;
                }
                out.append(failure.map(reason -> "  KEPT " + branch + " — " + reason)
                        .orElse("  deleted " + branch)).append('\n');
            }
        }
        if (candidates == 0) {
            // "nothing to prune" is an all-clear, so it must not be printed when no project could be read —
            // a human skimming the last line would conclude the repo is clean when nothing was examined.
            return out + (examined == 0
                    ? "no project could be examined — nothing was compared, see above."
                    : "nothing to prune.");
        }
        // Report what actually happened, not what was offered: git refuses a branch that is checked out in
        // some worktree, so "N candidates" and "N gone" are different numbers.
        return delete
                ? out + "deleted " + deleted + " of " + candidates + " branch(es); the rest are listed above."
                : out + "dry run — nothing deleted. `prune all` tries to delete the " + candidates
                        + " branch(es) above.";
    }

    /**
     * Which of the merged branches jagt may offer to delete. Excluded: the project's base and deploy branches
     * (long-lived, and always "merged" into the deploy branch), the branch the base repo currently has
     * checked out (git refuses to delete it), and every ACTIVE task's branch — a task whose work is already
     * in the deploy branch is still live until the human runs `done`.
     *
     * <p>All three refs are normalized the same way, so it does not matter which is which: they land in one
     * keep-set, and mixing up the arguments at the call site cannot un-protect any of them.
     */
    static List<String> prunable(List<String> merged, String baseBranch, String deployBranch,
                                 String currentBranch, Set<String> activeTaskBranches) {
        Set<String> keep = new HashSet<>(activeTaskBranches);
        for (String ref : List.of(baseBranch == null ? "" : baseBranch, deployBranch == null ? "" : deployBranch,
                currentBranch == null ? "" : currentBranch)) {
            keep.add(ref.replaceFirst("^origin/", ""));
        }
        return merged.stream().filter(branch -> !branch.isBlank() && !keep.contains(branch)).toList();
    }

    /** The MR url linked to a task (via ship/resume), or null. Resolves aliases. Used by `review`. */
    public String taskMrUrl(String taskId) {
        return requireTask(canonicalTaskId(taskId)).mrUrl();
    }

    /** A task's Tab-completion choice: its alias, id and title — the Master matches on alias/id and shows
     *  the title in the hint so the human recognises which task a bare number is. */
    public record TaskChoice(String alias, String id, String title) { }

    /** Every task, for the Master shell's Tab-completion of a {@code <ticket>} argument. */
    public List<TaskChoice> taskChoices() {
        List<TaskChoice> choices = new java.util.ArrayList<>();
        stateService.tasks().forEach((id, t) -> choices.add(new TaskChoice(t.alias(), id, t.title())));
        return choices;
    }

    /**
     * A clean `review` (CI green, no unresolved comments) IS a state transition: mark the task REVIEWED so
     * the dashboard's next move becomes `deploy`/`done` instead of looping back to `review`. Master-only.
     */
    public void markReviewed(String taskId) {
        markReviewOutcome(taskId, TaskStatus.REVIEWED, "reviewed — CI green, no unresolved comments");
    }

    /**
     * The MR was APPROVED by a human (green + no unresolved). Distinct from REVIEWED: a real approval, not
     * merely "nothing left to address". The auto-review poller lands here; the human's move is deploy/done.
     */
    public void markApproved(String taskId) {
        markReviewOutcome(taskId, TaskStatus.APPROVED, "approved — CI green, MR approved");
    }

    /** Sets a review-outcome status and pings the human ONCE on the transition (auto-poll runs unattended). */
    private void markReviewOutcome(String taskId, TaskStatus status, String message) {
        String id = canonicalTaskId(taskId);
        TaskStatus previous = stateService.task(id).map(TaskState::status).orElse(null);
        boolean updated = stateService.updateTask(id, t -> t.withStatus(status, message));
        // Only ping on a real transition of an existing task — never for a no-op (task gone) or a re-poll
        // that lands on the same status the human already saw.
        if (updated && status != previous) {
            userNotifier.notify("jagt · " + id, Move.forTask(status, true).hint());
        }
    }

    public String notifyUser(String title, String message) {
        userNotifier.notify(title == null ? "jagt" : title, message);
        return "Notification sent";
    }

    /**
     * Brings the task's agent window to the user's screen. If the session was
     * closed (window gone), a fresh Claude session is started first — focus
     * must always land somewhere.
     */
    public String focusTask(String taskId) {
        taskId = canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        ConfigService.ConfigFile config = configService.load();
        String session = agentSession(config, taskId);
        String dedicatedTitle = tmuxService.sessionName(config.viewer().tmuxSession());
        boolean respawned = false;
        Path worktreePath = Path.of(task.worktreePath());
        switch (tmuxService.taskWindowState(session, taskId)) {
            case MISSING -> {
                tmuxService.openTaskWindow(session, dedicatedTitle, taskId, task.alias(), worktreePath, false);
                respawned = true;
            }
            case DEAD_SHELL -> {
                // The window survived only for post-mortem inspection; focusing it
                // must hand the user a live agent, not a dead prompt.
                tmuxService.killTaskWindows(session, taskId);
                tmuxService.openTaskWindow(session, dedicatedTitle, taskId, task.alias(), worktreePath, false);
                respawned = true;
            }
            case AGENT_RUNNING -> {
            }
        }
        tmuxService.focusTaskWindow(session, dedicatedTitle, taskId);
        boolean raised = terminalDriver.reveal(dedicatedTitle);
        return "Focused tmux window '" + taskId + "'"
                + (raised
                        ? " and raised the agents window"
                        : " — but the agents viewer is a TAB, not a window: the terminal has no API to"
                                + " switch tabs, so click the agents tab yourself (or keep it as its own window)")
                + (respawned ? "; the session was dead, started a fresh " + agentRuntime.displayName() + " session" : "");
    }

    private String resolveTaskId(String explicitTaskId, String callerTaskId) {
        if (explicitTaskId == null || explicitTaskId.isBlank()) {
            if (callerTaskId == null) {
                throw new IllegalArgumentException(
                        "taskId is required: caller is not inside a registered worktree");
            }
            return callerTaskId;
        }
        String canonical = canonicalTaskId(explicitTaskId);
        // A sub-agent must never mutate a sibling task (classic LLM id mix-up).
        if (callerTaskId != null && !canonical.equals(callerTaskId)) {
            throw new IllegalArgumentException("Sub-agents may only act on their own task ("
                    + callerTaskId + "); omit taskId or use your own");
        }
        return canonical;
    }

    private TaskState requireTask(String taskId) {
        return stateService.task(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task " + taskId + " not found in state.json"));
    }

    /** Every taskId argument also accepts the task's short alias (p1, s2, ...). */
    private String canonicalTaskId(String idOrAlias) {
        return stateService.canonicalTaskId(idOrAlias);
    }

    /** First letter of the ticket + smallest free ordinal: ABC-123 -> a1, next ABC task -> a2. */
    private String nextAlias(String taskId) {
        String letter = taskId.substring(0, 1).toLowerCase();
        var used = stateService.tasks().values().stream()
                .map(TaskState::alias)
                .filter(a -> a != null)
                .collect(Collectors.toSet());
        for (int i = 1; ; i++) {
            String candidate = letter + i;
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
    }

    /** Starts the agent in a tmux window and returns its session name. */
    private String openTab(String taskId, String alias, Path worktreePath, ConfigService.ConfigFile config,
                           boolean planMode) {
        String session = agentSession(config, taskId);
        tmuxService.openTaskWindow(session, tmuxService.sessionName(config.viewer().tmuxSession()), taskId,
                alias, worktreePath, planMode);
        return session;
    }

    /**
     * viewMode "shared" (default): every task is a tmux window in ONE session,
     * one terminal tab total. viewMode "tab-per-task": each task gets its own
     * session, shown as its own Warp tab in the current window.
     */
    private String agentSession(ConfigService.ConfigFile config, String taskId) {
        String base = tmuxService.sessionName(config.viewer().tmuxSession());
        return config.viewer().sharedView()
                ? base
                : base + "-" + taskId;
    }

    private static GitService.BranchStrategy parseBranchStrategy(String value) {
        if (value == null || value.isBlank()) {
            return GitService.BranchStrategy.FRESH;
        }
        try {
            return GitService.BranchStrategy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown branchStrategy '" + value + "'. Allowed: fresh, recreate, resume");
        }
    }

    private static boolean planMode(String mode) {
        if (mode == null || mode.equalsIgnoreCase("auto")) {
            return false;
        }
        if (mode.equalsIgnoreCase("plan")) {
            return true;
        }
        throw new IllegalArgumentException("Unknown mode '" + mode + "'. Allowed: auto, plan");
    }

    /**
     * Every Claude session spawned by the orchestrator must know the whole system:
     * the master root, the backend, all configured projects and the other active
     * tasks — not only its own worktree.
     */
    private String subAgentContext(String taskId, String projectKey, ProjectConfig project, Path worktreePath,
                                   String remoteUrl, ConfigService.ConfigFile config) {
        String projectsTable = config.projects().entrySet().stream()
                .map(e -> "| " + e.getKey() + " | " + e.getValue().path() + " | " + e.getValue().baseBranch() + " |")
                .collect(Collectors.joining("\n"));
        String activeTasks = stateService.tasks().entrySet().stream()
                .map(e -> "- " + e.getKey() + " [" + e.getValue().status() + "] " + e.getValue().worktreePath())
                .collect(Collectors.joining("\n"));
        return prompts.subAgentContext().formatted(
                taskId,
                taskId, projectKey, project.path(), project.baseBranch(), remoteUrl, worktreePath,
                properties.watchdog().staleAfter().toMinutes() + " minutes",
                taskId,
                taskId, project.baseBranch(),
                paths.root(),
                paths.stateFile(),
                paths.configFile(),
                projectsTable.isBlank() ? "| (none) | | |" : projectsTable,
                activeTasks.isBlank() ? "- (none)" : activeTasks);
    }

    /**
     * Everything the AGENT needs in its fresh worktree — which of those files exist, and what is in them,
     * belongs to the runtime, not here: this method must never learn what a given agent's MCP config is called.
     */
    private void provisionForAgent(Path worktreePath) {
        agentRuntime.provisionWorktree(new AgentWorktree(worktreePath, paths.root(),
                configService.load().agent().outputStyleOrNull(), agentDisabledPlugins));
    }

    private static final Pattern URL = Pattern.compile("https?://\\S+");

    private static String extractUrl(String text) {
        if (text == null) {
            return null;
        }
        var m = URL.matcher(text);
        return m.find() ? m.group() : null;
    }

    /** Status messages render in one dashboard table line — cap them hard. */
    private static String abbreviate(String message) {
        if (message == null) {
            return null;
        }
        String flat = message.replaceAll("\\s+", " ").trim();
        return flat.length() <= 100 ? flat : flat.substring(0, 97) + "...";
    }

    private static void requireSafeId(String value, String name) {
        if (value == null || !SAFE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Argument '" + name + "' must match " + SAFE_ID.pattern()
                    + " (it becomes a branch, directory and tmux window name); got: " + value);
        }
    }
}
