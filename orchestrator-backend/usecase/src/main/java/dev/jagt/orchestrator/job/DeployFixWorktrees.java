package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.DeployTargets;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Opens, in the repository a red deploy pipeline ran for, the deploy worktree its fix is made in. */
@Component
@RequiredArgsConstructor
public class DeployFixWorktrees {

    private final DeployTargets targets;
    private final GitDeploy gitDeploy;

    public void open(TaskState task, String taskId, String project) {
        DeployTargets.Target target = targets.all(task).stream().filter(one -> one.project().equals(project))
                .findFirst().orElseThrow();
        gitDeploy.openFixWorktree(target.path(), taskId, target.deployBranch());
    }
}
