package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Facts;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.service.DeployTargets;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.TaskState;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeployConflicts {

  private final DeployTargets deployTargets;
  private final GitDeploy gitDeploy;

  /** The worktree {@code task} waits in, where it was handed back from a deploy conflict. */
  public Optional<WaitingConflict> waiting(String taskId, TaskState task) {
    return deployTargets
        .stopped(task)
        .filter(target -> !task.paused())
        .filter(target -> holds(task, taskId, target))
        .map(target -> conflict(taskId, task, target));
  }

  private boolean holds(TaskState task, String taskId, DeployTargets.Target target) {
    return Facts.revertConflicted(task)
        ? gitDeploy.hasRevertWorktree(target.path(), taskId)
        : gitDeploy.hasDeployWorktree(target.path(), taskId);
  }

  private WaitingConflict conflict(String taskId, TaskState task, DeployTargets.Target target) {
    if (Facts.revertConflicted(task)) {
      return new WaitingConflict(
          GitDeploy.revertWorktreePath(target.path(), taskId),
          gitDeploy.revertResolved(target.path(), taskId),
          gitDeploy.revertProgress(target.path(), taskId),
          null,
          true,
          Standing.of(task));
    }
    Path worktree = GitDeploy.deployWorktreePath(target.path(), taskId);
    return new WaitingConflict(
        worktree,
        gitDeploy.deployResolved(target.path(), taskId, target.deployBranch()),
        gitDeploy.deployProgress(target.path(), taskId),
        DeployTargets.handedBackForItsPipeline(task) ? task.message() : null,
        false,
        Standing.of(task));
  }

  /**
   * {@code red} is the line of a deploy handed back for its pipeline, null for a merge that
   * conflicts; {@code revert} is a revert's conflict, whose worktree is the revert's; {@code read}
   * is where the task stood when the conflict was found.
   */
  public record WaitingConflict(
      Path worktree,
      boolean resolved,
      Conflict.Progress progress,
      @Nullable String red,
      boolean revert,
      Standing read) {}
}
