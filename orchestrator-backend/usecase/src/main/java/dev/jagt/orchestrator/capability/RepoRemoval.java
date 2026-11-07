package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.service.RepoDeparture;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
class RepoRemoval {

  private final StateService stateService;
  private final PipelineRetry host;
  private final RepoDeparture departure;

  /** Refuses a project the task does not hold, its primary repository and one already deployed. */
  void vet(String taskId, TaskState task, String project) {
    if (task.repo(project).isEmpty()) {
      throw Refusal.byState("Task " + taskId + " does not work in " + project);
    }
    if (project.equals(task.project())) {
      throw Refusal.byState(project + " is the primary repository of " + taskId + " and stays");
    }
    if (task.deployment() != null
        && task.deployment().targets().stream()
            .anyMatch(target -> target.project().equals(project) && target.landed())) {
      throw Refusal.byState(project + " is deployed on " + taskId + ": revert first");
    }
  }

  /**
   * Closes the repository's open request, then takes it out of the task; a close that fails leaves
   * it on the task.
   */
  String remove(String taskId, String project) {
    TaskState task = stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
    TaskRepo repo =
        task.repo(project)
            .orElseThrow(() -> Refusal.byState("Task " + taskId + " does not work in " + project));
    String request = repo.mrUrl();
    boolean closed = false;
    if (request != null && repo.hasReviewRequest() && !repo.wasMerged()) {
      close(taskId, project, request);
      closed = true;
    }
    departure.leave(taskId, repo, "you removed it");
    return "remove-repo "
        + taskId
        + ": "
        + project
        + " left the task"
        + (closed ? ", its request closed" : "");
  }

  private void close(String taskId, String project, String requestUrl) {
    Answer<String> closed = host.closeRequest(project, requestUrl);
    if (closed.facts().isEmpty()) {
      throw new IllegalStateException(
          "the host did not close the request of " + project + ": " + closed.failure());
    }
    log.atInfo()
        .setMessage("request closed")
        .addKeyValue("task", taskId)
        .addKeyValue("project", project)
        .addKeyValue("request", requestUrl)
        .log();
  }
}
