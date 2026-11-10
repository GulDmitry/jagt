package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.flow.Facts;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.service.DeployTargets;
import dev.jagt.orchestrator.service.DeployTargets.Target;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The undo of a deploy, the other write to a shared branch; it works from the deploy's other end.
 */
@Service
@RequiredArgsConstructor
public class RevertService {

  private final StateService stateService;
  private final DeployTargets deployTargets;
  private final RevertRunner runner;
  private final GitDeploy gitDeploy;
  private final GitCaseFacts facts;

  private TaskState requireTask(String taskId) {
    return stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
  }

  /**
   * Undoes one deploy: reverts the merge commits it created on the deploy branches and pushes them.
   * The task branch keeps all its commits.
   */
  public Outcome revert(String taskId) {
    taskId = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(taskId);
    List<Target> landed = deployTargets.landed(task);
    Optional<Target> waiting = deployTargets.stopped(task);
    if (landed.isEmpty()) {
      if (waiting.isPresent()) {
        return runner.discard(taskId, waiting.get());
      }
      throw unrecordedDeploy(taskId, task);
    }
    landed.forEach(DeployTargets::requireDeployable);
    if (waiting.isPresent() && Facts.revertConflicted(task)) {
      requireResolved(taskId, waiting.get());
    }
    return runner.advance(taskId, task, landed, waiting);
  }

  /** A revert conflict is finished from a resolution, never over the paths still unmerged. */
  private void requireResolved(String taskId, Target at) {
    String unmerged = gitDeploy.revertUnmerged(at.path(), taskId);
    if (!unmerged.isBlank()) {
      throw Refusal.byState(
          "revert "
              + taskId
              + " waits on the conflict in "
              + GitDeploy.revertWorktreePath(at.path(), taskId)
              + ": still unmerged: "
              + unmerged.replace('\n', ' ')
              + ". Resolve and `git add` them, then revert again.");
    }
  }

  /**
   * Where the record holds no merge, a human picks one of the candidates git lists; a guess on a
   * shared branch is never made.
   */
  public Outcome revertPicked(String taskId, String pick) {
    taskId = stateService.canonicalTaskId(taskId);
    TaskState task = requireTask(taskId);
    if (!deployTargets.landed(task).isEmpty()) {
      throw Refusal.byState(
          "revert "
              + taskId
              + ": its record holds the merge, so `revert "
              + taskId
              + "` takes it out; a pick is for a task whose record holds none");
    }
    List<Target> targets = configured(task);
    for (Target target : targets) {
      Optional<GitCaseFacts.Candidate> found = facts.picked(candidates(taskId, target), pick);
      if (found.isPresent()) {
        return runner.picked(taskId, target, found.get().sha(), targets.size() == 1);
      }
    }
    throw unrecordedDeploy(taskId, task, "`" + pick + "` is none of the candidates. ");
  }

  private List<Target> configured(TaskState task) {
    return task.repos().stream()
        .map(repo -> deployTargets.configured(Objects.requireNonNull(repo.project())))
        .flatMap(Optional::stream)
        .toList();
  }

  private List<GitCaseFacts.Candidate> candidates(String taskId, Target target) {
    return facts.mergeCandidates(target.path(), target.deployBranch(), taskId);
  }

  private RuntimeException unrecordedDeploy(String taskId, TaskState task) {
    return unrecordedDeploy(taskId, task, "");
  }

  /** Guessing the merge commit would risk reverting the WRONG merge on a shared branch. */
  private RuntimeException unrecordedDeploy(String taskId, TaskState task, String lead) {
    List<String> lines = new ArrayList<>();
    for (TaskRepo repo : task.repos()) {
      String project = Objects.requireNonNull(repo.project());
      lines.add(
          project
              + ": "
              + deployTargets
                  .configured(project)
                  .map(target -> candidateLine(taskId, target))
                  .orElse("no deploy branch is configured, so none is read"));
    }
    return new IllegalStateException(
        lead
            + "revert "
            + taskId
            + ": jagt records no merge commit of this task's — nothing landed, or the deploy"
            + " predates that being stored — so it picks none. Candidates, newest first, at most "
            + GitCaseFacts.MAX_CANDIDATES
            + ": "
            + String.join(" | ", lines)
            + ". `revert "
            + taskId
            + " <sha>` takes the one you pick out.");
  }

  private String candidateLine(String taskId, Target target) {
    List<GitCaseFacts.Candidate> found = candidates(taskId, target);
    return "on "
        + target.deployBranch()
        + " "
        + (found.isEmpty()
            ? "no merge names the task"
            : found.stream().map(GitCaseFacts.Candidate::line).collect(Collectors.joining("; ")));
  }
}
