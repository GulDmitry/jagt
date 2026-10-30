package dev.jagt.orchestrator.capability.ide;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.service.DeployTargets;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A task opened as projects that run, or as the deploy worktree holding its conflict. */
@Component
@RequiredArgsConstructor
class IdeProject {

    private final DeployTargets deployTargets;
    private final GitDeploy gitDeploy;
    private final EditorDriver editorDriver;

    String open(String taskId, TaskState task) {
        // A DEPLOY_CONFLICT lives on the DEPLOY side; the task's own worktrees are clean.
        if (FlowRules.conflictedInTheDeployWorktree(task.status())) {
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
                + " (its uncommitted-changes view is the live diff vs base)";
    }

    /** Empty when no repository holds the conflict, the task's own worktrees being what to open then. */
    private Optional<String> openConflict(String taskId, TaskState task) {
        return deployTargets.stopped(task)
                .filter(target -> gitDeploy.hasDeployWorktree(target.path(), taskId))
                .map(target -> {
                    Path deployWorktree = GitDeploy.deployWorktreePath(target.path(), taskId);
                    editorDriver.open(deployWorktree);
                    return "Opened the deploy worktree " + deployWorktree + " — resolve there (`git add`),"
                            + " then `deploy " + taskId + "` again.";
                });
    }
}
