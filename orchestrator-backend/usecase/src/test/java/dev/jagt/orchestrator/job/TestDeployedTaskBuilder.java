package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.util.List;
import lombok.With;

@With
public record TestDeployedTaskBuilder(
    List<TaskRepo> repos, TaskStatus status, List<Deployment.Target> targets, int open) {

  public static TestDeployedTaskBuilder deployedTask() {
    return new TestDeployedTaskBuilder(
        List.of(new TaskRepo("shop", "/wt", "https://git.example.invalid/shop/web.git", null)),
        TaskStatus.DEPLOYED,
        List.of(Deployment.Target.of("shop", "dev").withMerge("0123456789abcdef")),
        1);
  }

  public TaskState build() {
    return TaskState.builder(repos, status)
        .deployment(new Deployment(1, targets, open, null))
        .build();
  }
}
