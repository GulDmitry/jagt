package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.agent.AgentRuntime;
import dev.jagt.orchestrator.agent.AgentWorktree;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.model.NewTask;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Creating a task: cut the worktree, put in what the agent needs, register it in state.json, start the agent.
 *
 * <p>This and {@link AgentSessions} left {@code OrchestratorTools} in ONE move, and that was the point — they
 * are what pulled six of its eleven collaborators ({@code TmuxService}, {@code TerminalDriver},
 * {@code AgentRuntime}, {@code PromptTemplates}, {@code OrchestratorPaths}, {@code OrchestratorProperties})
 * into a class that is otherwise about task state. Extracting anything else first only ADDED a dependency,
 * because a delegating facade keeps everything it does not shed.
 *
 * <p>What belongs to the AGENT rather than to jagt stays behind {@link AgentRuntime}: this class never learns
 * what a given agent's config file is called.
 */
@Service
@RequiredArgsConstructor
public class TaskProvisioning {

    /** Task ids become git branches, directory names and tmux window names/targets. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    private final ConfigService configService;
    private final StateService stateService;
    private final GitService gitService;
    private final AgentSessions agentSessions;
    private final AgentRuntime agentRuntime;
    private final OrchestratorProperties properties;
    private final OrchestratorPaths paths;
    private final PromptTemplates prompts;
    /** Plugins the agent sessions should NOT load — heavy LSP plugins spawn a ~1-2GB server per worktree. */
    @Value("${orchestrator.agent-disabled-plugins:}")
    private List<String> agentDisabledPlugins;

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

    public String initializeTask(NewTask request) {
        String taskId = request.taskId();
        String projectKey = request.projectKey();
        String instructions = request.instructions();
        requireSafeId(taskId, "taskId");
        requireSafeId(projectKey, "projectKey");
        boolean plan = AgentSessions.planMode(request.mode());
        GitService.BranchStrategy strategy = parseBranchStrategy(request.branchStrategy());
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
        String override = branchOverride(request.baseBranch(), projectPath, strategy);
        String baseBranch = override != null ? override : project.baseBranch();

        gitService.createWorktree(projectPath, worktreePath, taskId, baseBranch, strategy);
        String remoteUrl;
        try {
            remoteUrl = gitService.remoteUrl(projectPath);
            WorktreeFiles.excludeOrchestratorPlumbing(gitService.gitCommonDir(projectPath));
            provisionForAgent(worktreePath);
            WorktreeFiles.copyIdeProjectFiles(projectPath, worktreePath);
            WorktreeFiles.copyLocalFiles(projectPath, worktreePath,
                    configService.load().worktree().copyGlobsOrDefault());
            WorktreeFiles.write(worktreePath.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE),
                    subAgentContext(request, project, baseBranch, worktreePath, remoteUrl, config));
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
                .lastActiveTimestamp(System.currentTimeMillis()).alias(alias).remoteUrl(remoteUrl)
                .title(request.title())
                .ticketUrl(request.ticketUrl() == null || request.ticketUrl().isBlank() ? null : request.ticketUrl())
                // Only the OVERRIDE is persisted: a task that took the project default must keep following it.
                .baseBranch(override)
                .autoReview(config.autoReview().enabledOrDefault())
                .build());

        try {
            agentSessions.startAgent(taskId, alias, worktreePath, plan);
        } catch (RuntimeException e) {
            return "Task " + taskId + " registered and worktree created at " + worktreePath
                    + ", but the agent session failed to start: " + e.getMessage()
                    + " Fix the cause and call open_task_tab(\"" + taskId + "\") — do NOT call initialize_task again.";
        }

        return taskId + " is " + alias + " — agent running on " + taskId
                + (strategy == GitService.BranchStrategy.RESUME ? " (resumed)" : " from " + baseBranch) + "."
                + (plan ? " Plan mode: approve its plan in the agent window (focus " + alias + ")." : "");
    }

    /**
     * The human's chosen base branch, normalized to a bare name, or null when they named none. Checked against
     * the REMOTE before anything is created: the worktree is cut from {@code origin/<base>}, so a typo would
     * otherwise surface as a raw git failure after the branch and directory already exist.
     */
    private String branchOverride(String requested, Path projectPath, GitService.BranchStrategy strategy) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        String branch = requested.strip().replaceFirst("^origin/", "");
        // A RESUMED task is not cut from anything — the branch is only remembered as its review target, and
        // refusing the resume over it would strand a task whose request is open on this very branch.
        if (strategy != GitService.BranchStrategy.RESUME && !gitService.remoteBranchExists(projectPath, branch)) {
            throw new IllegalArgumentException("Base branch '" + branch + "' does not exist on origin — the"
                    + " worktree is cut from origin/" + branch + ", so check the name (or push it first).");
        }
        return branch;
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

    /**
     * Every agent session spawned by the orchestrator must know the whole system:
     * the master root, the backend, all configured projects and the other active
     * tasks — not only its own worktree.
     */
    private String subAgentContext(NewTask request, ProjectConfig project, String baseBranch,
                                   Path worktreePath, String remoteUrl, ConfigService.ConfigFile config) {
        String taskId = request.taskId();
        String projectsTable = config.projects().entrySet().stream()
                .map(e -> "| " + e.getKey() + " | " + e.getValue().path() + " | " + e.getValue().baseBranch() + " |")
                .collect(Collectors.joining("\n"));
        String activeTasks = stateService.tasks().entrySet().stream()
                .map(e -> "- " + e.getKey() + " [" + e.getValue().status() + "] " + e.getValue().worktreePath())
                .collect(Collectors.joining("\n"));
        return prompts.subAgentContext().formatted(
                taskId,
                taskId, request.projectKey(), project.path(), baseBranch, remoteUrl, worktreePath,
                properties.watchdog().staleAfter().toMinutes() + " minutes",
                taskId,
                taskId, baseBranch,
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

    /** Public because the MCP facade validates a resumed ticket id BEFORE it spends git calls
     *  resolving the project — one pattern, one message, in the class that owns task creation. */
    public static void requireSafeId(String value, String name) {
        if (!isSafeId(value)) {
            throw new IllegalArgumentException("Argument '" + name + "' must match " + SAFE_ID.pattern()
                    + " (it becomes a branch, directory and tmux window name); got: " + value);
        }
    }

    /** For callers that must EXPLAIN an unusable id rather than throw the generic one (see `resume`). */
    public static boolean isSafeId(String value) {
        return value != null && SAFE_ID.matcher(value).matches();
    }
}
