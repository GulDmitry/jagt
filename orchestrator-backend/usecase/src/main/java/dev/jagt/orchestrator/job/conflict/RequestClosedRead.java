package dev.jagt.orchestrator.job.conflict;

import dev.jagt.orchestrator.flow.deploy.ConflictEnding;
import dev.jagt.orchestrator.port.JobFaults;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.assistant.ReviewReader;
import dev.jagt.orchestrator.service.repo.MergedRequests;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reads the request of the repository a deploy was handed back on, and takes it out of the task
 * once the host closed it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class RequestClosedRead implements ConflictRead {

  private final ReviewReader reviewReader;
  private final MergedRequests mergedRequests;
  private final StateService stateService;
  private final JobFaults faults;

  @Override
  public ConflictEnding ending() {
    return ConflictEnding.REQUEST_CLOSED;
  }

  @Override
  public boolean read(String taskId, TaskState task) {
    return conflictedRepo(task)
        .filter(repo -> !toldGone(task, repo))
        .map(repo -> readRequest(taskId, repo))
        .orElse(false);
  }

  private static boolean toldGone(TaskState task, TaskRepo repo) {
    return task.round()
        .toldOrNone()
        .said(ToldOnce.Kind.CONFLICT_REQUEST_GONE, Objects.requireNonNull(repo.mrUrl()));
  }

  private static Optional<TaskRepo> conflictedRepo(TaskState task) {
    return Optional.ofNullable(task.work().deployment())
        .flatMap(Deployment::conflictedTarget)
        .map(Deployment.Target::project)
        .flatMap(project ->
            task.repos().stream()
                .filter(repo -> project.equals(repo.project()) && repo.hasReviewRequest())
                .findFirst());
  }

  private void reportGone(String taskId, String request) {
    stateService.reduce(
        taskId,
        (reducer, task) -> reducer.told(task, ToldOnce.Kind.CONFLICT_REQUEST_GONE, request));
  }

  private boolean readRequest(String taskId, TaskRepo repo) {
    try {
      boolean closed =
          reviewReader
              .read(taskId, Objects.requireNonNull(repo.mrUrl()))
              .facts()
              .filter(facts -> facts.request().exists() && facts.request().closed())
              .isPresent();
      if (closed && !mergedRequests.hostSaidClosed(taskId, repo)) {
        reportGone(taskId, Objects.requireNonNull(repo.mrUrl()));
      }
      return closed;
    } catch (RuntimeException failed) {
      faults.faulted(Causes.of(failed));
      log.atWarn()
          .setMessage("request of the conflicted repository not read")
          .addKeyValue("task", taskId)
          .addKeyValue("project", repo.project())
          .addKeyValue("cause", failed.toString())
          .addKeyValue("effect", "read again in a minute")
          .log();
      return false;
    }
  }
}
