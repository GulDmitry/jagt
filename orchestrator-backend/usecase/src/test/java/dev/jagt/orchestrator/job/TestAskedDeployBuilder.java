package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestAskedDeployBuilder(
    TaskStatus status,
    @Nullable String askedDigest,
    String worktree,
    @Nullable String alias,
    String merge) {

  public static TestAskedDeployBuilder askedDeploy() {
    return new TestAskedDeployBuilder(
        TaskStatus.DEPLOYED, "digest", "/wt", null, "0123456789abcdef");
  }

  public TaskState build() {
    return TaskState.builder("proj", worktree, status)
        .alias(alias)
        .build()
        .withDeployment(
            new Deployment(
                1, List.of(Deployment.Target.of("proj", "dev").withMerge(merge)), 1, askedDigest));
  }
}
