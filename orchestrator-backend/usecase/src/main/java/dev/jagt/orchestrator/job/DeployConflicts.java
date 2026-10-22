package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.DeployTargets;

import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.ConflictWatch;
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
        stateService.tasks().forEach((taskId, task) -> deployTargets.stopped(task, taskId)
                .ifPresent(target -> waiting.put(taskId, conflict(taskId, target))));
        return waiting;
    }

    private WaitingConflict conflict(String taskId, DeployTargets.Target target) {
        Path worktree = GitDeploy.deployWorktreePath(target.path(), taskId);
        return new WaitingConflict(worktree, gitDeploy.deployResolved(target.path(), taskId, target.deployBranch()),
                gitDeploy.deployProgress(target.path(), taskId));
    }

    public record WaitingConflict(Path worktree, boolean resolved, ConflictWatch.Progress progress) {
    }
}
