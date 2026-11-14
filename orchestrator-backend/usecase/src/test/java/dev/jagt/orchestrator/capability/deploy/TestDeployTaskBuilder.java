package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestDeployTaskBuilder(
    List<TaskRepo> repos,
    TaskStatus status,
    @Nullable String message,
    @Nullable Deployment deployment) {

  public static TestDeployTaskBuilder projectTask() {
    return new TestDeployTaskBuilder(
        List.of(TaskRepo.of("proj", "/wt")), TaskStatus.APPROVED, null, null);
  }

  public static TestDeployTaskBuilder apiAndWebTask() {
    return new TestDeployTaskBuilder(
        List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
        TaskStatus.APPROVED,
        null,
        null);
  }

  public TaskState build() {
    return TaskState.builder(repos, status)
        .alias("a1")
        .message(message)
        .deployment(deployment)
        .build();
  }
}
