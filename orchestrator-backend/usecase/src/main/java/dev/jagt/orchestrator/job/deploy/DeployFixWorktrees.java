package dev.jagt.orchestrator.job.deploy;

import dev.jagt.orchestrator.port.JobFaults;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.deploy.DeployTargets;
import dev.jagt.orchestrator.service.git.GitDeploy;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Opens, in the repository a red deploy pipeline ran for, the deploy worktree its fix is made in.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeployFixWorktrees {

  private final DeployTargets targets;
  private final GitDeploy gitDeploy;
  private final JobFaults faults;

  /** The cause where the worktree could not be opened; empty once it is. */
  public Optional<String> open(TaskState task, String taskId, String project) {
    try {
      DeployTargets.Target target =
          targets.landed(task).stream()
              .filter(one -> one.project().equals(project))
              .findFirst()
              .orElseThrow();
      gitDeploy.openFixWorktree(target.path(), taskId, target.deployBranch());
      return Optional.empty();
    } catch (RuntimeException e) {
      String cause = Causes.of(e);
      faults.faulted(cause);
      log.atWarn()
          .setMessage("deploy worktree not opened")
          .addKeyValue("task", taskId)
          .addKeyValue("project", project)
          .addKeyValue("cause", cause)
          .addKeyValue("effect", "the deploy stays open, tried again")
          .log();
      return Optional.of(cause);
    }
  }
}
