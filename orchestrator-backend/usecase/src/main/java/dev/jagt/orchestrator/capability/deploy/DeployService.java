package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.service.DeployTargets;
import dev.jagt.orchestrator.service.DeployTargets.Target;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * One of the only two operations that write a SHARED branch; it does not check that the caller is the human, that
 * gate sits outside. A task spanning repositories lands one at a time and stops at the first conflict: a shared
 * branch cannot be written atomically, so the half-state is reported.
 */
@Service
@RequiredArgsConstructor
public class DeployService {

    private final StateService stateService;
    private final DeployTargets deployTargets;
    private final GitDeploy gitDeploy;
    private final DeployRunner runner;

    private TaskState requireTask(String taskId) {
        return stateService.task(taskId)
                .orElseThrow(() -> Refusal.noSuchTask(taskId));
    }

    public Outcome deploy(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        List<Target> targets = deployTargets.all(task);
        targets.forEach(DeployTargets::requireDeployable);
        Optional<Target> waiting = waitingAt(task, taskId);
        requireTheFixStaged(task, taskId, waiting);
        if (resumeFrom(targets, waiting) == 0) {
            runner.open(taskId, targets);
        }
        return runner.advance(taskId);
    }

    /** A deploy handed back for its pipeline finishes with the session's fix, not with a merge of the task again. */
    private void requireTheFixStaged(TaskState task, String taskId, Optional<Target> waiting) {
        waiting
                .filter(target -> DeployTargets.handedBackForItsPipeline(task)
                        && !gitDeploy.deployResolved(target.path(), taskId, target.deployBranch()))
                .ifPresent(target -> {
                    throw Refusal.byState("deploy " + taskId + " waits on the session's fix in "
                            + GitDeploy.deployWorktreePath(target.path(), taskId) + ": it is not staged in full yet."
                            + " Stage it, then deploy again.");
                });
    }

    /** A recorded conflict whose worktree is gone cannot be resumed, and merging from the first repository again would land twice. */
    private Optional<Target> waitingAt(TaskState task, String taskId) {
        Optional<Target> waiting = deployTargets.stopped(task);
        waiting.filter(target -> !gitDeploy.hasDeployWorktree(target.path(), taskId)).ifPresent(target -> {
            throw Refusal.byState("deploy " + taskId + " waits on a conflict in " + target.project()
                    + " but its deploy worktree " + GitDeploy.deployWorktreePath(target.path(), taskId)
                    + " is gone. `revert " + taskId + "` clears it.");
        });
        return waiting;
    }

    /**
     * Where a repeated deploy picks the sequence up. Only a task HANDED BACK from a conflict has one; a worktree
     * left over from any other round is not a resume point, and jumping to it would skip the ones before it.
     */
    private int resumeFrom(List<Target> targets, Optional<Target> waiting) {
        return targets.size() == 1 ? 0 : waiting.map(targets::indexOf).orElse(0);
    }
}
