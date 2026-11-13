package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestAskedDeployBuilder(TaskStatus status, @Nullable String askedDigest) {

  public static TestAskedDeployBuilder askedDeploy() {
    return new TestAskedDeployBuilder(TaskStatus.DEPLOYED, "digest");
  }

  public TaskState build() {
    return TaskState.builder("proj", "/wt", status)
        .build()
        .withDeployment(
            new Deployment(
                1,
                List.of(Deployment.Target.of("proj", "dev").withMerge("0123456789abcdef")),
                1,
                askedDigest));
  }
}
