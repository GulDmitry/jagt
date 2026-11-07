package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Facts;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskCapability;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RepoCapability implements TaskCapability {

  private static final int CAUSE_WIDTH = 80;

  private final RepoAttachment attachment;

  @Override
  public TaskAction action() {
    return TaskAction.ADD_REPO;
  }

  @Override
  public void vet(String project) {
    attachment.vet(project);
  }

  @Override
  public void vet(String taskId, TaskState task, String project) {
    if (task.repo(project).isPresent()) {
      throw Refusal.byState("Task " + taskId + " already works in " + project);
    }
    vet(project);
  }

  @Override
  public Facts naming(Facts facts) {
    return facts.withWorkspace(facts.workspace().withRepoRequested(true));
  }

  @Override
  public Outcome run(String taskId, String project) {
    attachment.ask(taskId, project);
    return run(taskId);
  }

  @Override
  public Outcome run(String taskId) {
    try {
      return Outcome.relayed(attachment.attach(taskId), "repository added");
    } catch (RuntimeException failed) {
      log.atWarn()
          .setMessage("repository not added")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", failed.toString())
          .addKeyValue("effect", "the request stands; the card says why")
          .log();
      String cause = OneLine.of(String.valueOf(failed.getMessage()), CAUSE_WIDTH);
      return Outcome.partial(
              "add-repo " + taskId + " failed: " + failed.getMessage(),
              "add-repo failed: " + cause,
              failed)
          .asking();
    }
  }
}
