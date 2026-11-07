package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskCapability;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RemoveRepoCapability implements TaskCapability {

  private final RepoRemoval removal;

  @Override
  public TaskAction action() {
    return TaskAction.REMOVE_REPO;
  }

  @Override
  public void vet(String taskId, TaskState task, String project) {
    removal.vet(taskId, task, project);
  }

  @Override
  public Outcome run(String taskId) {
    throw Refusal.byState("remove-repo needs the project: " + TaskAction.REMOVE_REPO.usage());
  }

  @Override
  public Outcome run(String taskId, String project) {
    try {
      return Outcome.relayed(removal.remove(taskId, project), "repository removed");
    } catch (RuntimeException failed) {
      log.atWarn()
          .setMessage("repository not removed")
          .addKeyValue("task", taskId)
          .addKeyValue("project", project)
          .addKeyValue("cause", failed.toString())
          .addKeyValue("effect", "the repository stays on the task")
          .log();
      return Outcome.partial(
          "remove-repo " + taskId + " failed: " + failed.getMessage(), null, failed);
    }
  }
}
