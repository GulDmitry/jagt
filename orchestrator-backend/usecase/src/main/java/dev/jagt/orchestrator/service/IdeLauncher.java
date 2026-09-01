package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.EditorDriver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

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
        Path worktree = Path.of(task.worktreePath());
        if ("diff".equalsIgnoreCase(mode)) {
            return openDiff(taskId, task, worktree);
        }
        if (mode != null && !mode.isBlank() && !"project".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Unknown ide mode '" + mode + "'. Allowed: project, diff");
        }
        // A DEPLOY_CONFLICT lives on the DEPLOY side; the task's own worktree is clean.
        if (task.status() == TaskStatus.DEPLOY_CONFLICT) {
            // A task spanning repositories conflicts in exactly one of them, not necessarily the one the session
            // runs in, nor the first whose derived path exists, since siblings share it.
            var projects = configService.load().projects();
            for (var repo : task.repos()) {
                ProjectConfig conflicted = projects.get(repo.project());
                if (conflicted == null || !gitService.hasDeployWorktree(Path.of(conflicted.path()), taskId)) {
                    continue;
                }
                Path deployWorktree = GitService.deployWorktreePath(Path.of(conflicted.path()), taskId);
                editorDriver.open(deployWorktree);
                return "Opened the deploy worktree " + deployWorktree + " — resolve there (`git add`), then"
                        + " `deploy " + taskId + "` again.";
            }
        }
        editorDriver.open(worktree);
        return "Opened " + task.worktreePath() + " as a project in the editor"
                + " (use Git → Local Changes for a live diff vs base)";
    }

    /** The right side is FROZEN at this call: the editor's own Refresh does nothing. */
    private String openDiff(String taskId, TaskState task, Path worktree) {
        ProjectConfig project = configService.project(task.project());
        Path projectPath = Path.of(project.path());
        // The effective base, read exactly as the request's target is.
        String configured = task.baseBranchOr(project.baseBranch());
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Project " + task.project() + " has no baseBranch in jagt.yml,"
                    + " so a diff of " + taskId + " has nothing to read against");
        }
        String diffBase = "origin/" + configured.replaceFirst("^origin/", "");
        Path base = gitService.checkoutBaseForDiff(projectPath, diffBase, taskId);
        Path clean = gitService.checkoutWorktreeCleanForDiff(worktree, projectPath, diffBase, taskId);
        editorDriver.openDiff(base, clean);
        return "Opened STATIC diff of " + taskId + " (changes vs " + diffBase
                + ") — snapshot, does not refresh; re-run for a fresh one";
    }
}
