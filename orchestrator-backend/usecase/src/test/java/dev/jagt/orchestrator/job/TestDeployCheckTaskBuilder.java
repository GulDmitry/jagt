package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import java.util.List;
import lombok.With;

@With
public record TestDeployCheckTaskBuilder(TaskStatus status, String merge, boolean unread) {

  public static TestDeployCheckTaskBuilder deployCheckTask() {
    return new TestDeployCheckTaskBuilder(TaskStatus.DEPLOYED, "0123456789abcdef", false);
  }

  public TaskState build() {
    return TaskState.builder("proj", "/wt", status)
        .build()
        .withDeployment(
            new Deployment(
                1,
                List.of(Deployment.Target.of("proj", "dev").withMerge(merge).withUnread(unread)),
                1,
                null));
  }
}
