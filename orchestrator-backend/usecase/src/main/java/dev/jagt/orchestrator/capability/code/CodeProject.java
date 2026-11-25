package dev.jagt.orchestrator.capability.code;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.port.terminal.EditorDriver;
import dev.jagt.orchestrator.service.deploy.DeployTargets;
import dev.jagt.orchestrator.service.git.GitDeploy;
import dev.jagt.orchestrator.service.git.GitRevert;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A task opened as projects that run, or as the deploy or revert worktree holding its conflict. */
@Component
@RequiredArgsConstructor
class CodeProject {

  private final DeployTargets deployTargets;
  private final GitDeploy gitDeploy;
  private final GitRevert gitRevert;
  private final EditorDriver editorDriver;

  String open(String taskId, TaskState task) {
    if (editorDriver.absent()) {
      return "Opened " + taskId + " in the board's code viewer";
    }
    // A DEPLOY_CONFLICT lives on the DEPLOY side; the task's own worktrees are clean.
    if (FlowRules.conflictedInTheDeployWorktree(task.standing().status())) {
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
    return "Opened "
        + String.join(", ", opened)
        + (opened.size() > 1 ? " as projects, one window each" : " as a project")
        + " in the editor"
        + " (its uncommitted-changes view is the live diff vs base)";
  }

  /**
   * Empty when no repository holds the conflict, the task's own worktrees being what to open then.
   */
  private Optional<String> openConflict(String taskId, TaskState task) {
    boolean reverting = Facts.revertConflicted(task);
    return deployTargets
        .stopped(task)
        .filter(target ->
            reverting
                ? gitRevert.hasRevertWorktree(target.path(), taskId)
                : gitDeploy.hasDeployWorktree(target.path(), taskId))
        .map(target -> {
          Path worktree =
              reverting
                  ? gitRevert.revertWorktreePath(target.path(), taskId)
                  : gitDeploy.deployWorktreePath(target.path(), taskId);
          editorDriver.open(worktree);
          return "Opened the "
              + (reverting ? "revert" : "deploy")
              + " worktree "
              + worktree
              + " — resolve there (`git add`), then `"
              + (reverting ? "revert " : "deploy ")
              + taskId
              + "` again.";
        });
  }
}
