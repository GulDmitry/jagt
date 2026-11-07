package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.Remote;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import java.nio.file.Path;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** A task's base moved after it was cut: taken from git, never on the reporter's word. */
@Service
@RequiredArgsConstructor
public class TaskRetarget {

  private final StateService state;
  private final GitWorktrees git;
  private final ConfigService configService;

  public String retarget(String taskId, String requested) {
    TaskState task = state.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
    Remote remote = configService.project(Objects.requireNonNull(task.project())).gitRemote();
    String branch = Objects.requireNonNull(NewTaskWorktrees.branchOverride(requested, remote));
    for (TaskRepo repo : task.repos()) {
      Path worktree = Path.of(repo.worktreePath());
      if (!git.remoteBranchExists(worktree, branch)) {
        throw Refusal.byState(
            repo.project() + "'s " + remote.name() + " has no branch '" + branch + "'.");
      }
      if (!git.standsOn(worktree, branch)) {
        throw Refusal.byState(
            repo.project()
                + "'s HEAD does not hold "
                + remote.track(branch)
                + "; rebase onto it, then report it again.");
      }
    }
    state.updateTask(taskId, current -> current.withBaseBranch(branch));
    return "The base of "
        + taskId
        + " is now "
        + branch
        + ": ship targets it, and the diff is read against it.";
  }
}
