package dev.jagt.orchestrator.service.repo;

import dev.jagt.orchestrator.flow.Generation;
import dev.jagt.orchestrator.port.MergedRefresh;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.git.WorktreeInspection;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Which of a task's requests git finds merged into their target, kept on the task for the flow to
 * read.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MergedRequests implements MergedRefresh {

  private final StateService stateService;
  private final ConfigService configService;
  private final WorktreeInspection inspection;
  private final WorktreeChanges worktreeChanges;
  private final RepoDeparture departure;

  /**
   * Reads every request the host has not said merged, records what git says, merged or not, and
   * answers whether none is left to ship. A read git could not answer leaves the record as it is.
   */
  @Override
  public boolean refresh(String taskId) {
    TaskState task = stateService.task(taskId).orElse(null);
    if (task == null) {
      return false;
    }
    Generation readAt = Generation.of(Generation.Kind.ROUND, task);
    leaveWorkless(taskId, task);
    for (TaskRepo repo : task.repos()) {
      if (repo.hasReviewRequest() && !repo.wasHostMerged()) {
        mergedNow(taskId, task, repo)
            .filter(now -> now != repo.wasMerged())
            .ifPresent(now -> record(taskId, Objects.requireNonNull(repo.project()), now, readAt));
      }
    }
    return stateService.task(taskId).map(TaskState::requestsMerged).orElse(false);
  }

  private void leaveWorkless(String taskId, TaskState task) {
    if (!task.hasReviewRequest()) {
      return;
    }
    for (TaskRepo repo : task.repos()) {
      if (!repo.hasReviewRequest() && !holdsWork(task, repo)) {
        departure.leaveWorkless(taskId, repo, "it holds no work");
      }
    }
  }

  /**
   * The host said {@code project}'s request is merged, which git cannot see for a squash or a
   * fast-forward; its word outlasts git's.
   */
  public void hostSaidMerged(String taskId, String project, Generation readAt) {
    boolean known =
        stateService
            .task(taskId)
            .map(task ->
                task.repos().stream()
                    .anyMatch(repo -> project.equals(repo.project()) && repo.wasHostMerged()))
            .orElse(true);
    if (!known) {
      stateService.reduce(
          taskId,
          (reducer, current) ->
              reducer.observed(current, readAt, seen -> reducer.mergedByHost(seen, project)));
      log.atInfo()
          .setMessage("request merged")
          .addKeyValue("task", taskId)
          .addKeyValue("project", project)
          .addKeyValue("by", "host")
          .log();
    }
  }

  /**
   * The host said {@code repo}'s request was closed unmerged: where the task holds other
   * repositories, it leaves the task and this answers true; the task's primary repository, alone or
   * not, and one already deployed stay, for the caller to refuse.
   */
  public boolean hostSaidClosed(String taskId, TaskRepo repo) {
    return departure.leave(taskId, repo, "its request was closed on the host");
  }

  private void record(String taskId, String project, boolean merged, Generation readAt) {
    stateService.reduce(
        taskId,
        (reducer, current) ->
            reducer.observed(current, readAt, seen -> reducer.mergedByGit(seen, project, merged)));
    log.atInfo()
        .setMessage(merged ? "request merged" : "request no longer merged")
        .addKeyValue("task", taskId)
        .addKeyValue("project", project)
        .addKeyValue("by", "git")
        .log();
  }

  /**
   * The commit each request's branch last had pushed, one per repository; blank where the
   * repository cannot name every one.
   */
  public String pushedTips(String taskId) {
    return stateService.task(taskId).map(worktreeChanges::pushedTips).orElse("");
  }

  /**
   * The projects among these repositories with no request though one is owed: a repository with no
   * code host is owed none.
   */
  public List<String> owingRequest(TaskState task) {
    ConfigFile config = configService.load();
    return task.repos().stream()
        .filter(repo ->
            !repo.hasReviewRequest()
                && !config.hasNoCodeHost(Objects.requireNonNull(repo.project()))
                && holdsWork(task, repo))
        .map(TaskRepo::project)
        .toList();
  }

  private boolean holdsWork(TaskState task, TaskRepo repo) {
    String project = Objects.requireNonNull(repo.project());
    return worktreeChanges.holdsWork(
        project,
        Objects.requireNonNull(repo.worktreePath()),
        WorktreeChanges.targetBranch(task, configService.project(project)));
  }

  private Path projectPath(String project) {
    return Path.of(configService.project(project).path()).toAbsolutePath().normalize();
  }

  private Optional<Boolean> mergedNow(String taskId, TaskState task, TaskRepo repo) {
    String project = Objects.requireNonNull(repo.project());
    try {
      return Optional.of(
          inspection.mergedIntoTarget(
              projectPath(project),
              Path.of(Objects.requireNonNull(repo.worktreePath())),
              WorktreeChanges.targetBranch(task, configService.project(project))));
    } catch (RuntimeException e) {
      log.atWarn()
          .setMessage("merged read failed")
          .addKeyValue("task", taskId)
          .addKeyValue("project", project)
          .addKeyValue("cause", e.toString())
          .log();
      return Optional.empty();
    }
  }
}
