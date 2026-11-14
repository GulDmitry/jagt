package dev.jagt.orchestrator.job.deploy;

import dev.jagt.orchestrator.service.deploy.DeployTargets;
import dev.jagt.orchestrator.service.git.GitDeploy;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Opens, in the repository a red deploy pipeline ran for, the deploy worktree its fix is made in.
 */
@Component
@RequiredArgsConstructor
public class DeployFixWorktrees {

  private final DeployTargets targets;
  private final GitDeploy gitDeploy;

  public void open(TaskState task, String taskId, String project) {
    DeployTargets.Target target =
        targets.landed(task).stream()
            .filter(one -> one.project().equals(project))
            .findFirst()
            .orElseThrow();
    gitDeploy.openFixWorktree(target.path(), taskId, target.deployBranch());
  }
}
