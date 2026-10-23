package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.task.GitTrust;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskName;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Refuses a new task before anything exists, then cuts every worktree it needs, or none. */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewTaskWorktrees {

    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    /** Past this many tasks opening on the same words, the words are the problem. */
    private static final int MAX_SAME_NAME = 20;
    /** A ceiling rather than a queue: a board nobody can read is the failure this refuses, not a busy machine. */
    static final int MAX_TASKS = 24;

    private final ConfigService configService;
    private final StateService stateService;
    private final GitWorktrees gitWorktrees;
    private final WorktreeSetup worktreeSetup;

    /** An empty {@code projectKeys} asks every configured project — what a task with no project named yet needs. */
    public @Nullable String existingBranchProject(String taskId, @Nullable Collection<String> projectKeys) {
        ConfigService.ConfigFile config = configService.load();
        Collection<String> keys = projectKeys == null || projectKeys.isEmpty()
                ? config.projects().keySet() : projectKeys;
        return keys.stream()
                .filter(config.projects()::containsKey)
                .filter(k -> gitWorktrees.branchExists(
                        Path.of(Objects.requireNonNull(config.projects().get(k)).path()).toAbsolutePath().normalize(), taskId))
                .findFirst().orElse(null);
    }

    /** Nobody stands at intake to choose, and neither answer here loses work: the branch's own commits resume. */
    public BranchStrategy strategyForExisting(String taskId, String projectKey) {
        ProjectConfig project = Objects.requireNonNull(configService.load().projects().get(projectKey));
        Path path = Path.of(project.path()).toAbsolutePath().normalize();
        if (!gitWorktrees.branchExists(path, taskId)) {
            return BranchStrategy.FRESH;
        }
        return gitWorktrees.holdsOwnCommits(path, taskId, project.baseBranch())
                ? BranchStrategy.RESUME : BranchStrategy.RECREATE;
    }

    /**
     * {@code base}, or the first {@code base-2}, {@code base-3}… no task and no branch has taken. The human did
     * not choose this name, so a collision is stepped over rather than refused back at them.
     */
    public String freeTaskName(String base, List<String> projectKeys) {
        for (int suffix = 1; suffix <= MAX_SAME_NAME; suffix++) {
            String candidate = suffix == 1 ? base : base + "-" + suffix;
            if (!taken(candidate, projectKeys)) {
                return candidate;
            }
        }
        throw Refusal.byState(MAX_SAME_NAME + " tasks are already called '" + base
                + "' — open the line with different words");
    }

    private boolean taken(String name, List<String> projectKeys) {
        return stateService.tasks().keySet().stream()
                .anyMatch(registered -> TaskName.slug(registered).equals(TaskName.slug(name)))
                || existingBranchProject(name, projectKeys) != null;
    }

    /** What git obeys around the worktrees just cut, null where it could not be read. */
    public @Nullable GitTrust trust(List<NewRepo> repos) {
        try {
            return GitTrust.of(gitWorktrees.gitState(repos.stream().map(NewRepo::worktreePath).toList()));
        } catch (RuntimeException unreadable) {
            log.atWarn().setMessage("git state unread").addKeyValue("cause", unreadable.toString())
                    .addKeyValue("effect", "trusted at the first check").log();
            return null;
        }
    }

    /** Every repository of {@code request}, each worktree cut; a refusal comes before anything is. */
    public List<NewRepo> cut(NewTask request, ConfigService.ConfigFile config, BranchStrategy strategy) {
        String taskId = request.taskId();
        TaskName.require(taskId, "taskId");
        requireOwnBranch(taskId, request.baseBranch(), config);
        if (stateService.tasks().size() >= MAX_TASKS) {
            throw Refusal.byState(MAX_TASKS + " tasks are already open, which is the limit —"
                    + " finish one with `done` before starting another.");
        }
        if (stateService.task(taskId).isPresent()) {
            throw Refusal.byState("Task " + taskId + " is already registered in state.json. "
                    + "Use open_task_tab to respawn its agent, or ask the human to press done on it first.");
        }
        // Two branches flatten to one directory, and cutting the second clears the first as a stale worktree.
        stateService.tasks().keySet().stream()
                .filter(registered -> TaskName.slug(registered).equals(TaskName.slug(taskId)))
                .findFirst()
                .ifPresent(registered -> {
                    throw Refusal.byState("Task " + taskId + " and " + registered + " both become"
                            + " the directory " + TaskName.slug(taskId) + ". Retire " + registered
                            + " first, or take a different branch.");
                });
        List<NewRepo> repos = resolveRepos(request, config, strategy);
        cutWorktrees(request, repos, strategy);
        return repos;
    }

    private List<NewRepo> resolveRepos(NewTask request, ConfigService.ConfigFile config,
                                       BranchStrategy strategy) {
        List<NewRepo> repos = new ArrayList<>();
        for (String projectKey : request.projectKeys()) {
            requireSafeProjectKey(projectKey);
            ProjectConfig project = configured(projectKey, config);
            Path projectPath = Path.of(project.path()).toAbsolutePath().normalize();
            String override = branchOverride(request.baseBranch());
            if (override != null) {
                requireOnOrigin(override, projectKey, projectPath, strategy);
            }
            repos.add(new NewRepo(projectKey, project, projectPath,
                    Objects.requireNonNull(projectPath.getParent()).resolve(TaskName.slug(request.taskId()) + "-" + projectKey),
                    gitWorktrees.gitCommonDir(projectPath),
                    override != null ? override : project.baseBranch(),
                    gitWorktrees.remoteUrl(projectPath), repos.isEmpty()));
        }
        if (repos.isEmpty()) {
            throw new IllegalArgumentException("A task needs at least one project");
        }
        return List.copyOf(repos);
    }

    private static ProjectConfig configured(String projectKey, ConfigService.ConfigFile config) {
        ProjectConfig project = config.projects().get(projectKey);
        if (project == null) {
            throw new IllegalArgumentException(
                    "Unknown project '" + projectKey + "'. Known projects: " + config.projects().keySet());
        }
        return project;
    }

    /**
     * One more repository for a registered task, cut as creation cuts them and registered after the others. Every
     * worktree's briefing and sandbox are written again for the new set; a failure leaves nothing of it.
     */
    public NewRepo attach(String taskId, String projectKey, ConfigService.ConfigFile config) {
        TaskState task = stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
        List<NewRepo> held = held(task, config);
        if (held.stream().anyMatch(repo -> repo.project().equals(projectKey))) {
            throw Refusal.byState("Task " + taskId + " already works in " + projectKey);
        }
        ProjectConfig project = configured(projectKey, config);
        NewTask request = NewTask.builder(taskId, projectKey).mode(task.planGate() == PlanGate.REQUIRED ? "plan" : null)
                .baseBranch(task.baseBranch()).build();
        Optional<Path> standing = gitWorktrees.worktreeOf(Path.of(project.path()).toAbsolutePath().normalize(), taskId);
        BranchStrategy strategy = standing.isPresent() ? BranchStrategy.RESUME : strategyForExisting(taskId, projectKey);
        NewRepo resolved = resolveRepos(request, config, strategy).getFirst().besideTheSession();
        NewRepo added = standing.map(resolved::at).orElse(resolved);
        if (standing.isEmpty()) {
            gitWorktrees.createWorktree(added.projectPath(), added.worktreePath(), taskId, added.baseBranch(), strategy);
        }
        try {
            worktreeSetup.attach(request, added, Stream.concat(held.stream(), Stream.of(added)).toList());
            GitTrust addedTrust = trust(List.of(added));
            stateService.updateTask(taskId, registered -> trusted(registered.withRepo(added.registered()), addedTrust));
        } catch (RuntimeException e) {
            // A worktree jagt did not cut holds work jagt never saw: it stays.
            if (standing.isEmpty()) {
                unwind(request, List.of(added), List.of(added), strategy);
            }
            throw e;
        }
        return added;
    }

    /** A task trusted at its first check stays so; else the added repository joins what was trusted as it stood. */
    private static TaskState trusted(TaskState task, @Nullable GitTrust added) {
        return task.gitTrust() == null || added == null ? task : task.withGitTrust(task.gitTrust().including(added));
    }

    private List<NewRepo> held(TaskState task, ConfigService.ConfigFile config) {
        List<NewRepo> held = new ArrayList<>();
        for (TaskRepo repo : task.repos()) {
            ProjectConfig project = configured(Objects.requireNonNull(repo.project()), config);
            Path projectPath = Path.of(project.path()).toAbsolutePath().normalize();
            String remote = repo.remoteUrl() == null ? gitWorktrees.remoteUrl(projectPath) : repo.remoteUrl();
            held.add(new NewRepo(repo.project(), project, projectPath, Path.of(Objects.requireNonNull(repo.worktreePath())),
                    gitWorktrees.gitCommonDir(projectPath), task.baseBranchOr(project.baseBranch()), remote,
                    held.isEmpty()));
        }
        return held;
    }

    /**
     * A half-created task burns its id: the branch and directory exist while nothing is registered, so a retry hits
     * "branch already exists". A failure anywhere therefore unwinds every repository already cut.
     */
    private void cutWorktrees(NewTask request, List<NewRepo> repos, BranchStrategy strategy) {
        List<NewRepo> cut = new ArrayList<>();
        try {
            for (NewRepo repo : repos) {
                gitWorktrees.createWorktree(repo.projectPath(), repo.worktreePath(), request.taskId(),
                        repo.baseBranch(), strategy);
                cut.add(repo);
                worktreeSetup.fill(request, repo, repos);
            }
        } catch (RuntimeException e) {
            unwind(request, cut, repos, strategy);
            throw e;
        }
    }

    /** Every worktree {@link #cut} made goes again, for a task whose registration failed after it. */
    public void uncut(NewTask request, List<NewRepo> repos, BranchStrategy strategy) {
        unwind(request, repos, repos, strategy);
    }

    private void unwind(NewTask request, List<NewRepo> cut, List<NewRepo> repos, BranchStrategy strategy) {
        // The branch goes with the worktree only where THIS call created it: a resumed task's branch was
        // already there with the human's commits.
        String branchToDelete = strategy == BranchStrategy.RESUME ? null : request.taskId();
        cut.forEach(repo -> gitWorktrees.removeWorktree(repo.projectPath(), repo.worktreePath(), branchToDelete));
        // A resumed branch survives, so the repository jagt detached to free it must go back.
        if (branchToDelete == null) {
            repos.forEach(repo -> gitWorktrees.reattach(repo.projectPath(), request.taskId()));
        }
    }

    static @Nullable String branchOverride(@Nullable String requested) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        String branch = requested.strip().replaceFirst("^origin/", "");
        String unusable = TaskName.unusableReason(branch);
        if (unusable != null) {
            throw new IllegalArgumentException("Base branch '" + branch + "' is not a branch name: " + unusable);
        }
        return branch;
    }

    /** A task's branch is rebased and force-pushed, so it must not be one other work lands on. */
    private static void requireOwnBranch(String taskId, @Nullable String baseOverride, ConfigService.ConfigFile config) {
        String task = ProjectConfig.localName(taskId);
        Stream.concat(config.projects().values().stream()
                                .flatMap(project -> Stream.of(project.baseBranch(), project.deployBranch())),
                        Stream.of(baseOverride))
                .map(ProjectConfig::localName)
                .filter(task::equalsIgnoreCase)
                .findFirst()
                .ifPresent(shared -> {
                    throw new IllegalArgumentException("Task " + taskId + " would be the shared branch " + shared
                            + ", which a task rebases and pushes. Name the task after a branch of its own.");
                });
    }

    /**
     * Checked against the REMOTE before anything is created: the worktree is cut from {@code origin/<base>}, so a
     * typo would otherwise surface as a raw git failure after the branch and directory exist.
     */
    private void requireOnOrigin(String branch, String projectKey, Path projectPath,
                                 BranchStrategy strategy) {
        // A RESUMED task is not cut from anything: the branch is only remembered as its review target.
        if (strategy != BranchStrategy.RESUME && !gitWorktrees.remoteBranchExists(projectPath, branch)) {
            throw Refusal.byState("Base branch '" + branch + "' does not exist on "
                    + projectKey + "'s origin — the worktree is cut from origin/" + branch + ", so check the"
                    + " name (or push it there first).");
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
