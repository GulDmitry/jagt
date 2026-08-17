package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.agent.AgentRuntime;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.model.NewRepo;
import dev.jagt.orchestrator.model.NewTask;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Creating a task: cut the worktree, put in what the agent needs, register it in state.json, start the agent.
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
    private final WorktreeSetup worktreeSetup;

    /**
     * The configured project whose repo already has a branch named taskId, or null if none. An empty
     * {@code projectKeys} asks every configured project, which is what a task with no project named yet needs.
     */
    public String existingBranchProject(String taskId, Collection<String> projectKeys) {
        ConfigService.ConfigFile config = configService.load();
        Collection<String> keys = projectKeys == null || projectKeys.isEmpty()
                ? config.projects().keySet() : projectKeys;
        return keys.stream()
                .filter(config.projects()::containsKey)
                .filter(k -> gitService.branchExists(
                        Path.of(config.projects().get(k).path()).toAbsolutePath().normalize(), taskId))
                .findFirst().orElse(null);
    }

    public String initializeTask(NewTask request) {
        String taskId = request.taskId();
        requireSafeId(taskId, "taskId");
        boolean plan = AgentSessions.planMode(request.mode());
        GitService.BranchStrategy strategy = parseBranchStrategy(request.branchStrategy());
        if (stateService.task(taskId).isPresent()) {
            throw new IllegalArgumentException("Task " + taskId + " is already registered in state.json. "
                    + "Use open_task_tab to respawn its agent or remove_task to retire it first.");
        }
        ConfigService.ConfigFile config = configService.load();
        List<NewRepo> repos = resolveRepos(request, config, strategy);
        NewRepo session = repos.get(0);
        cutWorktrees(request, repos, strategy);

        String alias = nextAlias(taskId);
        stateService.putTask(taskId, TaskState.builder(repos.stream().map(NewRepo::registered).toList(),
                        TaskStatus.NEW)
                .lastActiveTimestamp(System.currentTimeMillis()).alias(alias)
                .title(request.title())
                .ticketUrl(request.ticketUrl() == null || request.ticketUrl().isBlank() ? null : request.ticketUrl())
                // Only the OVERRIDE is persisted: a task that took the project default must keep following it.
                .baseBranch(branchOverride(request.baseBranch()))
                .autoReview(config.autoReview().enabledOrDefault())
                .build());

        try {
            agentSessions.startAgent(taskId, alias, session.worktreePath(), plan);
        } catch (RuntimeException e) {
            return "Task " + taskId + " registered and worktree created at " + session.worktreePath()
                    + ", but the agent session failed to start: " + e.getMessage()
                    + " Fix the cause and call open_task_tab(\"" + taskId + "\") — do NOT call initialize_task again.";
        }

        return taskId + " is " + alias + " — agent running on " + taskId
                + (strategy == GitService.BranchStrategy.RESUME ? " (resumed)" : " from " + session.baseBranch())
                + alsoIn(repos) + "."
                + (plan ? " Plan mode: approve its plan in the agent window (focus " + alias + ")." : "");
    }

    /** Every repository the task will work in, validated to the last field before anything is created. */
    private List<NewRepo> resolveRepos(NewTask request, ConfigService.ConfigFile config,
                                       GitService.BranchStrategy strategy) {
        List<NewRepo> repos = new ArrayList<>();
        for (String projectKey : request.projectKeys()) {
            requireSafeId(projectKey, "projectKey");
            ProjectConfig project = config.projects().get(projectKey);
            if (project == null) {
                throw new IllegalArgumentException(
                        "Unknown project '" + projectKey + "'. Known projects: " + config.projects().keySet());
            }
            Path projectPath = Path.of(project.path()).toAbsolutePath().normalize();
            String override = branchOverride(request.baseBranch());
            if (override != null) {
                requireOnOrigin(override, projectKey, projectPath, strategy);
            }
            repos.add(new NewRepo(projectKey, project, projectPath,
                    projectPath.getParent().resolve(request.taskId() + "-" + projectKey),
                    gitService.gitCommonDir(projectPath),
                    override != null ? override : project.baseBranch(),
                    gitService.remoteUrl(projectPath), repos.isEmpty()));
        }
        if (repos.isEmpty()) {
            throw new IllegalArgumentException("A task needs at least one project");
        }
        return List.copyOf(repos);
    }

    /**
     * A half-created task burns its id — the branch and directory exist while nothing is registered, so a retry
     * hits "branch already exists". With several repositories the same is true of the ones already cut, so a
     * failure anywhere unwinds all of them.
     */
    private void cutWorktrees(NewTask request, List<NewRepo> repos, GitService.BranchStrategy strategy) {
        List<NewRepo> cut = new ArrayList<>();
        try {
            for (NewRepo repo : repos) {
                gitService.createWorktree(repo.projectPath(), repo.worktreePath(), request.taskId(),
                        repo.baseBranch(), strategy);
                cut.add(repo);
                worktreeSetup.fill(request, repo, repos);
            }
        } catch (RuntimeException e) {
            // The branch goes with the worktree only where THIS call created it. A resumed task's branch was
            // already there with the human's commits, and force-deleting it would take work nothing can restore.
            String branchToDelete = strategy == GitService.BranchStrategy.RESUME ? null : request.taskId();
            cut.forEach(repo -> gitService.removeWorktree(repo.projectPath(), repo.worktreePath(),
                    branchToDelete));
            throw e;
        }
    }

    /** Nothing for ordinary single-repo work; the sibling repositories named when there are any. */
    private static String alsoIn(List<NewRepo> repos) {
        return repos.size() < 2 ? "" : ", also in " + repos.stream().skip(1).map(NewRepo::project)
                .collect(Collectors.joining(", "));
    }

    /** The human's chosen base branch as a bare name, or null when they named none. */
    private static String branchOverride(String requested) {
        return requested == null || requested.isBlank()
                ? null
                : requested.strip().replaceFirst("^origin/", "");
    }

    /**
     * Checked against the REMOTE before anything is created: the worktree is cut from {@code origin/<base>}, so
     * a typo would otherwise surface as a raw git failure after the branch and directory already exist.
     */
    private void requireOnOrigin(String branch, String projectKey, Path projectPath,
                                 GitService.BranchStrategy strategy) {
        // A RESUMED task is not cut from anything — the branch is only remembered as its review target, and
        // refusing the resume over it would strand a task whose request is open on this very branch.
        if (strategy != GitService.BranchStrategy.RESUME && !gitService.remoteBranchExists(projectPath, branch)) {
            throw new IllegalArgumentException("Base branch '" + branch + "' does not exist on "
                    + projectKey + "'s origin — the worktree is cut from origin/" + branch + ", so check the"
                    + " name (or push it there first).");
        }
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

    /** Public so a resume can validate the id BEFORE spending git calls: one pattern, one message. */
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
