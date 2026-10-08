package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.DeployTargets;

import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.StateService;
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
                .ifPresent(target -> waiting.put(taskId, new WaitingConflict(
                        GitDeploy.deployWorktreePath(target.path(), taskId),
                        gitDeploy.deployResolved(target.path(), taskId, target.deployBranch())))));
        return waiting;
    }

    public record WaitingConflict(Path worktree, boolean resolved) {
    }
}
