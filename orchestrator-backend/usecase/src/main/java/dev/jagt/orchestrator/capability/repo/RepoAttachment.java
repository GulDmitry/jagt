package dev.jagt.orchestrator.capability.repo;

import dev.jagt.orchestrator.flow.session.SessionStanding;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.NewTaskWorktrees;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.NewRepo;
import dev.jagt.orchestrator.task.turn.Step;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
class RepoAttachment {

  private final ConfigService configService;
  private final StateService stateService;
  private final NewTaskWorktrees worktrees;
  private final StepRelay relay;

  /**
   * Cuts and registers the repository the task's session asked for, and tells the session where it
   * is.
   */
  String attach(String taskId) {
    String id = stateService.canonicalTaskId(taskId);
    TaskState task = stateService.task(id).orElseThrow(() -> Refusal.noSuchTask(taskId));
    String project = task.work().repoRequest();
    if (project == null) {
      throw Refusal.byState("Task " + id + " asked for no repository");
    }
    NewRepo added = worktrees.attach(id, project, configService.load());
    log.atInfo()
        .setMessage("repository added")
        .addKeyValue("task", id)
        .addKeyValue("project", project)
        .addKeyValue("path", added.worktreePath())
        .log();
    return told(id, added, SessionStanding.of(task));
  }

  /** Makes {@code project} the task's repository request, as if its session had asked. */
  void ask(String taskId, String project) {
    stateService.reduce(taskId, (reducer, task) -> reducer.repoAsked(task, project));
  }

  /** Refuses a project {@code jagt.yml} does not hold. */
  void vet(String project) {
    Set<String> known = configService.load().projects().keySet();
    if (!known.contains(project)) {
      throw Refusal.byState("Unknown project '" + project + "'. Known projects: " + known);
    }
  }

  private String told(String taskId, NewRepo added, SessionStanding read) {
    String where = added.project() + " is at " + added.worktreePath();
    try {
      relay.relay(
          taskId,
          Step.REPO,
          "jagt added the repository you asked for: "
              + where
              + ", on branch "
              + taskId
              + ", cut from "
              + added.baseBranch()
              + ".",
          StepRelay.Delivery.UNDER,
          read);
      return "add-repo " + taskId + ": " + where + "; the session was told";
    } catch (RuntimeException unheard) {
      log.atWarn()
          .setMessage("session not told of repository")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", unheard.toString())
          .addKeyValue(
              "effect", "the repository is added; the session learns it from its next brief")
          .log();
      return "add-repo "
          + taskId
          + ": "
          + where
          + "; the session could not be told ("
          + unheard.getMessage()
          + "), focus it";
    }
  }
}
