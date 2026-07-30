package dev.jawo.orchestrator.mcp;

import dev.jawo.orchestrator.config.OrchestratorPaths;
import dev.jawo.orchestrator.config.OrchestratorProperties;
import dev.jawo.orchestrator.config.PromptTemplates;
import dev.jawo.orchestrator.model.ProjectConfig;
import dev.jawo.orchestrator.model.TaskState;
import dev.jawo.orchestrator.model.TaskStatus;
import dev.jawo.orchestrator.platform.EditorDriver;
import dev.jawo.orchestrator.platform.TerminalDriver;
import dev.jawo.orchestrator.platform.UserNotifier;
import dev.jawo.orchestrator.service.ConfigService;
import dev.jawo.orchestrator.service.GitService;
import dev.jawo.orchestrator.service.StateService;
import dev.jawo.orchestrator.service.TmuxService;
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
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implements the MCP tools exposed to Master and Sub-agent Claude sessions.
 * The callerTaskId (resolved from the X-Working-Directory header) scopes
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
    /** Plugins the agent sessions should NOT load — heavy LSP plugins spawn a ~1-2GB JDT server per
     *  worktree and agents don't need them (they have file tools). Field-injected so the many test
     *  constructors need no change; null in tests = disable nothing. */
    @Value("${orchestrator.agent-disabled-plugins:}")
    private List<String> agentDisabledPlugins;

    public OrchestratorTools(ConfigService configService, StateService stateService, GitService gitService,
                             TmuxService tmuxService, EditorDriver editorDriver, TerminalDriver terminalDriver,
                             UserNotifier userNotifier, OrchestratorProperties properties, OrchestratorPaths paths,
                             PromptTemplates prompts) {
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

    public String initializeTask(String taskId, String projectKey, String instructions, String mode,
                                 String branchStrategy, String title) {
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
            excludeOrchestratorFiles(projectPath);
            linkOrchestratorFiles(worktreePath);
            copyRunConfigurations(projectPath, worktreePath);
            copyLocalFiles(projectPath, worktreePath, configService.load().worktreeCopyGlobsOrDefault());
            writeString(worktreePath.resolve("CLAUDE.md"),
                    subAgentContext(taskId, projectKey, project, worktreePath, remoteUrl, config));
            if (instructions != null && !instructions.isBlank()) {
                writeString(worktreePath.resolve("task_context.md"), instructions);
            }
        } catch (RuntimeException e) {
            // Compensate: without this, the taskId is burned (branch + worktree exist,
            // nothing registered) and a retry hits "branch already exists".
            gitService.removeWorktree(projectPath, worktreePath, taskId);
            throw e;
        }

        String alias = nextAlias(taskId);
        stateService.putTask(taskId, new TaskState(projectKey, worktreePath.toString(), TaskStatus.NEW,
                System.currentTimeMillis(), null, alias, remoteUrl, title, null));

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
                + "- Claude sub-agent started in tmux window '" + taskId + "' of session '" + session
                + "' (a Warp window attaches automatically; manual: tmux attach -t " + session + ")\n"
                + (plan
                        ? "- PLAN MODE: the agent plans first; the human approves the plan in its tmux window\n"
                        : "")
                + "- sub-agent context written to CLAUDE.md"
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
            return url != null ? next.withMrUrl(url) : next;
        });
        if (!updated) {
            throw new IllegalArgumentException("Task " + taskId + " not found in state.json");
        }
        // Event-driven: the human doesn't poll, so ping them the moment a task hands
        // control back (finished review / broke CI). Only on the transition, never on
        // the agent's frequent IN_PROGRESS keep-alives.
        if (newStatus != previous && (newStatus == TaskStatus.REVIEW_PENDING || newStatus == TaskStatus.CI_FAILED)) {
            userNotifier.notify("jawo · " + taskId, dev.jawo.orchestrator.model.NextMove.forStatus(newStatus));
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
        return "New Claude session started for " + taskId + " in tmux window '" + taskId + "' of session '"
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
            Path base = gitService.checkoutBaseForDiff(projectPath, project.baseBranch(), taskId);
            Path clean = gitService.checkoutWorktreeCleanForDiff(worktree, projectPath, project.baseBranch(), taskId);
            editorDriver.openDiff(base, clean);
            return "Opened STATIC diff of " + taskId + " (changes vs " + project.baseBranch()
                    + ") — snapshot, does not refresh; re-run for a fresh one";
        }
        if (mode != null && !mode.isBlank() && !"project".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Unknown ide mode '" + mode + "'. Allowed: project, diff");
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
        writeString(Path.of(task.worktreePath()).resolve("task_context.md"), instructions);
        // A file on disk doesn't wake a running Claude session — nudge it directly.
        String session = agentSession(configService.load(), taskId);
        if (tmuxService.taskWindowState(session, taskId) == TmuxService.WindowState.AGENT_RUNNING
                && tmuxService.nudgeTaskWindow(session, taskId,
                        "The Master updated task_context.md — re-read it now and follow the new instructions.")) {
            return "Instructions written to task_context.md and the agent was nudged to re-read them.";
        }
        return "Instructions written to " + task.worktreePath() + "/task_context.md, but the agent session"
                + " is NOT running — respawn it (open_task_tab/focus); it reads task_context.md on start.";
    }

    /** Closes the task's tmux window(s), killing the Claude session; worktree and state stay. */
    public String closeTaskTab(String taskId, String callerTaskId) {
        taskId = resolveTaskId(taskId, callerTaskId);
        TaskState task = requireTask(taskId);
        int killed = tmuxService.killTaskWindows(
                agentSession(configService.load(), taskId), taskId);
        return killed == 0
                ? "No tmux window named '" + taskId + "' found — the session was already closed."
                : "Closed " + killed + " tmux window(s) for " + taskId + "; the Claude session is terminated. "
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
            gitService.removeWorktree(Path.of(project.path()), Path.of(task.worktreePath()), null);
        } else {
            log.warn("Project '{}' of task {} no longer in config.json; skipping worktree removal", task.project(), taskId);
        }
        stateService.removeTask(taskId);
        // Reserve the viewer by default: keep it open when empty so a manual
        // placement (dragged into a group/window) survives across task cycles.
        boolean closedViewer = stateService.tasks().isEmpty() && !config.keepViewerOrDefault();
        if (closedViewer) {
            terminalDriver.closeViewerWindow(tmuxService.sessionName(config.tmuxSession()));
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
    public String resumeTask(String taskId, String mrUrl) {
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
        initializeTask(taskId, projectKey, instructions, null, "resume", null);
        updateAgentStatus("CI_POLLING", "MR: " + mrUrl, taskId, null);
        return "Resumed " + taskId + " on its existing branch; linked MR " + mrUrl
                + "; status CI_POLLING — run `review` or `deploy`.";
    }

    private String projectForMrUrl(String mrUrl) {
        for (var e : configService.load().projects().entrySet()) {
            String path = gitProjectPath(gitService.remoteUrl(Path.of(e.getValue().path())));
            if (path != null && mrUrl.contains(path)) {
                return e.getKey();
            }
        }
        throw new IllegalArgumentException("no configured project matches MR url: " + mrUrl);
    }

    /** {@code git@host:group/proj.git} or {@code https://host/group/proj(.git)} -> {@code group/proj}. */
    static String gitProjectPath(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return null;
        }
        String s = remoteUrl.trim();
        if (s.endsWith(".git")) {
            s = s.substring(0, s.length() - 4);
        }
        if (s.startsWith("http")) {
            int host = s.indexOf('/', s.indexOf("://") + 3);
            return host < 0 ? null : s.substring(host + 1);
        }
        int colon = s.indexOf(':');
        return colon < 0 ? null : s.substring(colon + 1);
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
        // target the base/release branch that tasks are cut from. jawo never writes there.
        String base = project.baseBranch() == null ? "" : project.baseBranch().replaceFirst("^origin/", "");
        if (project.deployBranch().equals(base)) {
            throw new IllegalArgumentException("REFUSED: deployBranch equals the base branch '" + base
                    + "'. jawo must never merge into the branch tasks are created from — point deployBranch"
                    + " at a downstream branch (e.g. dev).");
        }
        gitService.mergeIntoAndPush(Path.of(project.path()), taskId, project.deployBranch());
        // deploy IS a state transition — mark it so the dashboard's next move is 'done', not 'review'.
        stateService.updateTask(taskId, t -> t.withStatus(TaskStatus.DEPLOYED, "deployed to " + project.deployBranch()));
        return "Merged branch " + taskId + " into " + project.deployBranch() + " and pushed; status -> DEPLOYED";
    }

    public String listTasks() {
        return stateService.prettyJson();
    }

    /** The MR url linked to a task (via ship/resume), or null. Resolves aliases. Used by `review`. */
    public String taskMrUrl(String taskId) {
        return requireTask(canonicalTaskId(taskId)).mrUrl();
    }

    /**
     * The human approved the current uncommitted changes. Ship is the ONLY commit point: it relays the
     * approval to the agent (which owns the GitLab MCP) via task_context.md — commit with the pattern
     * title, push, create the MR if absent (target = baseBranch), post any drafted review replies, and
     * report back CI_POLLING with the MR url. jawo itself never touches the remote.
     */
    public String ship(String taskId) {
        taskId = canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        if (task.status() != TaskStatus.REVIEW_PENDING) {
            throw new IllegalStateException("ship: " + taskId + " is " + task.status()
                    + ", not REVIEW_PENDING — nothing new to ship (already shipping/shipped, or still working).");
        }
        ProjectConfig project = configService.project(task.project());
        String baseBranch = project.baseBranch() == null ? "" : project.baseBranch().replaceFirst("^origin/", "");
        ConfigService.ConfigFile config = configService.load();
        String title = config.mrTitlePatternOrDefault()
                .replace("{ticket}", taskId)
                .replace("{title}", task.title() == null ? "" : task.title())
                .trim();
        String repliesStep;
        if (!config.postReviewRepliesOrDefault()) {
            repliesStep = "4. Do NOT post any replies — LEAVE review_replies.md untouched for the human to"
                    + " post; only the code is pushed.\n";
        } else if (config.reviewReplyAuthorsOrEmpty().isEmpty()) {
            repliesStep = "4. If review_replies.md exists, post each drafted reply to its MR thread, then"
                    + " delete it.\n";
        } else {
            repliesStep = "4. If review_replies.md exists, post drafted replies ONLY to threads whose comment"
                    + " author matches (case-insensitive) any of: "
                    + String.join(", ", config.reviewReplyAuthorsOrEmpty())
                    + ". Leave replies to OTHER authors as drafts (do NOT post them); delete only posted ones.\n";
        }
        String instruction = "This IS the human approval to ship. Do NOT re-verify, do NOT ask — do it now.\n"
                + "1. Commit ALL current changes with EXACTLY this message: \"" + title + "\".\n"
                + "2. Push branch " + taskId + ".\n"
                + "3. If no merge request exists for this branch yet, create one via your GitLab MCP:"
                + " source " + taskId + " -> target " + baseBranch + ", title \"" + title + "\".\n"
                + repliesStep
                + "5. Report back with update_agent_status CI_POLLING, message \"MR: <the merge request url>\".";
        writeTaskContext(taskId, instruction);
        // Flip to SHIPPING now so the dashboard shows ship is underway (the status only reaches
        // CI_POLLING when the agent reports back the MR) and a second `ship` is refused meanwhile.
        stateService.updateTask(taskId, t -> t.withStatus(TaskStatus.SHIPPING, "shipping"));
        return "ship " + taskId + ": approval relayed — agent will commit \"" + title
                + "\", push, ensure the MR, post replies, then report CI_POLLING.";
    }

    public String notifyUser(String title, String message) {
        userNotifier.notify(title == null ? "jawo" : title, message);
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
        String dedicatedTitle = tmuxService.sessionName(config.tmuxSession());
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
        tmuxService.focusTaskWindow(session, dedicatedTitle, taskId, worktreePath);
        boolean raised = terminalDriver.reveal(dedicatedTitle);
        return "Focused tmux window '" + taskId + "'"
                + (raised
                        ? " and raised the agents window"
                        : " — but the agents viewer is a TAB, not a window: the terminal has no API to"
                                + " switch tabs, so click the agents tab yourself (or keep it as its own window)")
                + (respawned ? "; the session was dead, started a fresh Claude session" : "");
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
        if (idOrAlias == null || stateService.task(idOrAlias).isPresent()) {
            return idOrAlias;
        }
        return stateService.tasks().entrySet().stream()
                .filter(e -> idOrAlias.equalsIgnoreCase(e.getValue().alias()))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse(idOrAlias);
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
        tmuxService.openTaskWindow(session, tmuxService.sessionName(config.tmuxSession()), taskId,
                alias, worktreePath, planMode);
        return session;
    }

    /**
     * viewMode "shared" (default): every task is a tmux window in ONE session,
     * one terminal tab total. viewMode "tab-per-task": each task gets its own
     * session, shown as its own Warp tab in the current window.
     */
    private String agentSession(ConfigService.ConfigFile config, String taskId) {
        String base = tmuxService.sessionName(config.tmuxSession());
        return config.viewMode() == null || "shared".equalsIgnoreCase(config.viewMode())
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

    private void linkOrchestratorFiles(Path worktreePath) {
        symlink(worktreePath.resolve("mcp_client.js"), paths.root().resolve("mcp_client.js"));
        symlink(worktreePath.resolve(".mcp.json"), paths.root().resolve(".mcp.json"));
        // Without this flag every spawned Claude session stops at an interactive
        // "New MCP server found" approval prompt and the sub-agent never starts.
        try {
            Files.createDirectories(worktreePath.resolve(".claude"));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create .claude dir in " + worktreePath, e);
        }
        // Server approval alone is not enough: Claude's auto-mode classifier still
        // gates individual MCP calls, silently freezing agents on invisible prompts
        // (even notify_user gets blocked) — pre-allow every jawo tool. The optional
        // agentOutputStyle from config.json is pinned here (a worktree is an untrusted
        // project where the human's global style may not apply); default null → omitted.
        writeString(worktreePath.resolve(".claude").resolve("settings.local.json"),
                agentSettingsJson(configService.load().agentOutputStyleOrNull(), agentDisabledPlugins));
    }

    /**
     * The generated worktree {@code .claude/settings.local.json}: pre-approves the jawo MCP tools,
     * optionally pins {@code agentOutputStyle}, and disables the given plugins for the agent (heavy
     * LSP plugins spawn a JDT server per worktree — agents don't need them). Valid JSON in all cases.
     */
    static String agentSettingsJson(String outputStyle, List<String> disabledPlugins) {
        String styleLine = outputStyle == null || outputStyle.isBlank() ? ""
                : "\n  \"outputStyle\": \"" + outputStyle.replace("\\", "\\\\").replace("\"", "\\\"") + "\",";
        String pluginsLine = "";
        if (disabledPlugins != null) {
            String entries = disabledPlugins.stream()
                    .filter(p -> p != null && !p.isBlank())
                    .map(p -> "\"" + p.strip() + "\": false")
                    .collect(Collectors.joining(", "));
            if (!entries.isBlank()) {
                pluginsLine = "\n  \"enabledPlugins\": {" + entries + "},";
            }
        }
        return """
                {%s%s
                  "enableAllProjectMcpServers": true,
                  "permissions": {
                    "allow": ["mcp__jawo-orchestrator"]
                  }
                }
                """.formatted(styleLine, pluginsLine);
    }

    private void symlink(Path link, Path target) {
        try {
            Files.deleteIfExists(link);
            Files.createSymbolicLink(link, target);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create symlink " + link + " -> " + target, e);
        }
    }

    /**
     * Keeps orchestrator plumbing out of `git status` in every worktree of the
     * project. info/exclude only affects untracked files, so a project's own
     * tracked CLAUDE.md is unaffected.
     */
    private void excludeOrchestratorFiles(Path projectPath) {
        List<String> entries = List.of("mcp_client.js", ".mcp.json", "CLAUDE.md", "task_context.md",
                "review_replies.md", ".claude/", ".run/");
        try {
            Path exclude = gitService.gitCommonDir(projectPath).resolve("info").resolve("exclude");
            Files.createDirectories(exclude.getParent());
            String current = Files.exists(exclude) ? Files.readString(exclude) : "";
            StringBuilder additions = new StringBuilder();
            for (String entry : entries) {
                if (current.lines().noneMatch(entry::equals)) {
                    additions.append(entry).append('\n');
                }
            }
            if (!additions.isEmpty()) {
                Files.writeString(exclude, current.isEmpty() || current.endsWith("\n")
                        ? current + additions
                        : current + "\n" + additions);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot update git info/exclude for " + projectPath, e);
        }
    }

    /**
     * A worktree opens in IntelliJ without the base project's run configurations
     * (they are gitignored in the base repo). Copy the "Store as project file"
     * ones over so `ide <ticket>` opens ready to run. IntelliJ keeps them in
     * `.run/` (modern default) and/or legacy `.idea/runConfigurations/` — copy
     * both, each relative to the project root. Best-effort; absent dir = no-op.
     */
    static void copyRunConfigurations(Path projectPath, Path worktreePath) {
        for (String dir : List.of(".run", ".idea/runConfigurations")) {
            copyTree(projectPath.resolve(dir), worktreePath.resolve(dir), worktreePath);
        }
    }

    /** Directories never worth scanning for local files (huge and/or generated). */
    private static final Set<String> COPY_SCAN_SKIP =
            Set.of(".git", "node_modules", "build", "target", "out", "dist", ".gradle", ".idea");

    /**
     * Copies gitignored LOCAL files matching the configured {@code worktreeCopyGlobs} from the base
     * repo to the same relative path in a new worktree — module {@code .env}, key files, SSL certs
     * etc. that the run configs reference but git omits, so the app can start in the worktree. The
     * patterns are per-project config, NOT hardcoded. Best-effort; heavy dirs skipped. (Secrets live
     * only in the local, gitignored worktree.)
     */
    static void copyLocalFiles(Path projectPath, Path worktreePath, List<String> globs) {
        var matchers = (globs == null ? List.<String>of() : globs).stream()
                .filter(g -> g != null && !g.isBlank())
                .map(g -> java.nio.file.FileSystems.getDefault().getPathMatcher("glob:" + g.strip()))
                .toList();
        if (matchers.isEmpty()) {
            return;
        }
        try {
            Files.walkFileTree(projectPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    return COPY_SCAN_SKIP.contains(d.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    Path rel = projectPath.relativize(f);
                    if (matchers.stream().anyMatch(m -> m.matches(rel))) {
                        Path to = worktreePath.resolve(rel);
                        try {
                            Files.createDirectories(to.getParent());
                            Files.copy(f, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            log.warn("Could not copy local file {} -> {}: {}", f, to, e.getMessage());
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Could not scan {} for local files: {}", projectPath, e.getMessage());
        }
    }

    private static void copyTree(Path source, Path target, Path worktreePath) {
        if (!Files.isDirectory(source)) {
            return;
        }
        try (var files = Files.walk(source)) {
            files.forEach(from -> {
                Path to = target.resolve(source.relativize(from));
                try {
                    if (Files.isDirectory(from)) {
                        Files.createDirectories(to);
                    } else {
                        Files.createDirectories(to.getParent());
                        Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot copy run configuration " + from, e);
                }
            });
        } catch (IOException e) {
            log.warn("Could not copy {} into {}: {}", source, worktreePath, e.getMessage());
        }
    }

    private void writeString(Path file, String content) {
        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
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
