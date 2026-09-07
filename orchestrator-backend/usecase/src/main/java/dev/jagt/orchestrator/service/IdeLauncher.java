package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.EditorDriver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdeLauncher {

    private final StateService stateService;
    private final ConfigService configService;
    private final GitService gitService;
    private final EditorDriver editorDriver;

    public String open(String taskIdOrAlias, String mode) {
        String taskId = stateService.canonicalTaskId(taskIdOrAlias);
        TaskState task = stateService.task(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task " + taskId + " not found in state.json"));
        if ("diff".equalsIgnoreCase(mode)) {
            return openDiff(taskId, task);
        }
        if (mode != null && !mode.isBlank() && !"project".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Unknown ide mode '" + mode + "'. Allowed: project, diff");
        }
        // A DEPLOY_CONFLICT lives on the DEPLOY side; the task's own worktrees are clean.
        if (task.status() == TaskStatus.DEPLOY_CONFLICT) {
            Optional<String> conflict = openConflict(taskId, task);
            if (conflict.isPresent()) {
                return conflict.get();
            }
        }
        List<String> opened = new ArrayList<>();
        for (TaskRepo repo : task.repos()) {
            editorDriver.open(Path.of(repo.worktreePath()));
            opened.add(repo.worktreePath());
        }
        return "Opened " + String.join(", ", opened)
                + (opened.size() > 1 ? " as projects, one window each" : " as a project") + " in the editor"
                + " (use Git → Local Changes for a live diff vs base)";
    }

    /** Empty when no repository holds the conflict, the task's own worktrees being what to open then. */
    private Optional<String> openConflict(String taskId, TaskState task) {
        // A task spanning repositories conflicts in exactly one of them, not necessarily the one the session
        // runs in, nor the first whose derived path exists, since siblings share it.
        var projects = configService.load().projects();
        for (TaskRepo repo : task.repos()) {
            ProjectConfig conflicted = projects.get(repo.project());
            if (conflicted == null || !gitService.hasDeployWorktree(Path.of(conflicted.path()), taskId)) {
                continue;
            }
            Path deployWorktree = GitService.deployWorktreePath(Path.of(conflicted.path()), taskId);
            editorDriver.open(deployWorktree);
            return Optional.of("Opened the deploy worktree " + deployWorktree + " — resolve there (`git add`),"
                    + " then `deploy " + taskId + "` again.");
        }
        return Optional.empty();
    }

    /** One comparison per repository, each right side FROZEN at this call: the editor's own Refresh does nothing. */
    private String openDiff(String taskId, TaskState task) {
        List<String> against = new ArrayList<>();
        boolean many = task.repos().size() > 1;
        for (TaskRepo repo : task.repos()) {
            against.add((many ? repo.project() : "changes") + " vs " + openDiffOf(taskId, task, repo));
        }
        return "Opened STATIC diff of " + taskId + " (" + String.join(", ", against)
                + ") — snapshot, does not refresh; re-run for a fresh one";
    }

    /** The base it was read against. */
    private String openDiffOf(String taskId, TaskState task, TaskRepo repo) {
        ProjectConfig project = configService.project(repo.project());
        Path projectPath = Path.of(project.path());
        // The effective base, read exactly as the request's target is.
        String configured = task.baseBranchOr(project.baseBranch());
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Project " + repo.project() + " has no baseBranch in jagt.yml,"
                    + " so a diff of " + taskId + " has nothing to read against");
        }
        String diffBase = "origin/" + configured.replaceFirst("^origin/", "");
        Path base = gitService.checkoutBaseForDiff(projectPath, diffBase, taskId, repo.project());
        Path clean = gitService.checkoutWorktreeCleanForDiff(Path.of(repo.worktreePath()), projectPath,
                diffBase, taskId, repo.project());
        editorDriver.openDiff(base, clean);
        return diffBase;
    }
}
