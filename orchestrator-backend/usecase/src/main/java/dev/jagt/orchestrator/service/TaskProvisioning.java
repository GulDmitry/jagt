package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskName;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What belongs to the AGENT rather than to jagt stays behind {@link AgentRuntime}: this class never learns what a
 * given agent's config file is called.
 */
@Service
@RequiredArgsConstructor
public class TaskProvisioning {

    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    private final ConfigService configService;
    private final StateService stateService;
    private final GitService gitService;
    private final AgentSessions agentSessions;
    private final WorktreeSetup worktreeSetup;

    /** An empty {@code projectKeys} asks every configured project — what a task with no project named yet needs. */
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
        TaskName.require(taskId, "taskId");
        boolean plan = AgentSessions.planMode(request.mode());
        GitService.BranchStrategy strategy = parseBranchStrategy(request.branchStrategy());
        if (stateService.task(taskId).isPresent()) {
            throw new IllegalArgumentException("Task " + taskId + " is already registered in state.json. "
                    + "Use open_task_tab to respawn its agent or remove_task to retire it first.");
        }
        // Two branches flatten to one directory, and cutting the second clears the first as a stale worktree.
        stateService.tasks().keySet().stream()
                .filter(registered -> TaskName.slug(registered).equals(TaskName.slug(taskId)))
                .findFirst()
                .ifPresent(registered -> {
                    throw new IllegalArgumentException("Task " + taskId + " and " + registered + " both become"
                            + " the directory " + TaskName.slug(taskId) + ". Retire " + registered
                            + " first, or take a different branch.");
                });
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
            requireSafeProjectKey(projectKey);
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
                    projectPath.getParent().resolve(TaskName.slug(request.taskId()) + "-" + projectKey),
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
            // A resumed branch survives, so a repository jagt detached to free it can go back — and it must:
            // the task does not exist afterwards, and nothing else would ever return that checkout.
            if (branchToDelete == null) {
                repos.forEach(repo -> gitService.reattach(repo.projectPath(), request.taskId()));
            }
            throw e;
        }
    }

    private static String alsoIn(List<NewRepo> repos) {
        return repos.size() < 2 ? "" : ", also in " + repos.stream().skip(1).map(NewRepo::project)
                .collect(Collectors.joining(", "));
    }

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

    /** A project key is a config key and a directory suffix, not a branch — it stays plain. */
    private static void requireSafeProjectKey(String value) {
        if (value == null || !SAFE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("Argument 'projectKey' must match " + SAFE_KEY.pattern()
                    + "; got: " + value);
        }
    }
}
