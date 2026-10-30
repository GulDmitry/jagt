package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Facts;
import dev.jagt.orchestrator.service.DeployTargets;

import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.TaskState;
import org.jspecify.annotations.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeployConflicts {

    private final StateService stateService;
    private final DeployTargets deployTargets;
    private final GitDeploy gitDeploy;

    /** Every task handed back from a deploy conflict, with the worktree it waits in. */
    public Map<String, WaitingConflict> waiting() {
        Map<String, WaitingConflict> waiting = new LinkedHashMap<>();
        stateService.tasks().forEach((taskId, task) -> deployTargets.stopped(task)
                .filter(target -> holds(task, taskId, target))
                .ifPresent(target -> waiting.put(taskId, conflict(taskId, task, target))));
        return waiting;
    }

    private boolean holds(TaskState task, String taskId, DeployTargets.Target target) {
        return Facts.revertConflicted(task) ? gitDeploy.hasRevertWorktree(target.path(), taskId)
                : gitDeploy.hasDeployWorktree(target.path(), taskId);
    }

    private WaitingConflict conflict(String taskId, TaskState task, DeployTargets.Target target) {
        if (Facts.revertConflicted(task)) {
            return new WaitingConflict(GitDeploy.revertWorktreePath(target.path(), taskId),
                    gitDeploy.revertResolved(target.path(), taskId), gitDeploy.revertProgress(target.path(), taskId),
                    null, true);
        }
        Path worktree = GitDeploy.deployWorktreePath(target.path(), taskId);
        return new WaitingConflict(worktree, gitDeploy.deployResolved(target.path(), taskId, target.deployBranch()),
                gitDeploy.deployProgress(target.path(), taskId),
                DeployTargets.handedBackForItsPipeline(task) ? task.message() : null, false);
    }

    /**
     * {@code red} is the line of a deploy handed back for its pipeline, null for a merge that conflicts;
     * {@code revert} is a revert's conflict, whose worktree is the revert's.
     */
    public record WaitingConflict(Path worktree, boolean resolved, Conflict.Progress progress,
                                  @Nullable String red, boolean revert) {
    }
}
