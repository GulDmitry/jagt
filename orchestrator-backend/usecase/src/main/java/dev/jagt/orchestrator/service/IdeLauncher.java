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
        // A DEPLOY_CONFLICT lives on the DEPLOY side: the task's own worktree is clean and has nothing to
        // resolve.
        if (task.status() == TaskStatus.DEPLOY_CONFLICT) {
            // A task spanning repositories conflicts in exactly one of them, and it is not necessarily the one
            // the session runs in — nor the first whose derived path exists, since siblings share it. A project
            // that has since left jagt.yml is skipped rather than thrown at the human: the worktree below can
            // still be opened.
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
        // Against what the task MERGES INTO, not what it was cut from, so a conflict-merged deploy does not
        // read as this task's change. A task with its own base diffs against THAT: nothing else contains it.
        String diffBase = task.baseBranch() != null && !task.baseBranch().isBlank()
                ? "origin/" + task.baseBranch()
                : (project.deployBranch() != null && !project.deployBranch().isBlank()
                        ? "origin/" + project.deployBranch() : project.baseBranch());
        Path base = gitService.checkoutBaseForDiff(projectPath, diffBase, taskId);
        Path clean = gitService.checkoutWorktreeCleanForDiff(worktree, projectPath, diffBase, taskId);
        editorDriver.openDiff(base, clean);
        return "Opened STATIC diff of " + taskId + " (changes vs " + diffBase
                + ") — snapshot, does not refresh; re-run for a fresh one";
    }
}
