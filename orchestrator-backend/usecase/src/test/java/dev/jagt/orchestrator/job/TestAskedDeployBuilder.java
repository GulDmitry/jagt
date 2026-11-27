package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestAskedDeployBuilder(
    TaskStatus status,
    @Nullable String askedDigest,
    @Nullable String worktree,
    @Nullable String alias,
    String merge,
    @Nullable String ticketUrl,
    @Nullable TaskStop stop) {

  public static TestAskedDeployBuilder askedDeploy() {
    return new TestAskedDeployBuilder(
        TaskStatus.DEPLOYED, "digest", "/wt", null, "0123456789abcdef", null, null);
  }

  public TaskState build() {
    return TaskState.builder("proj", worktree, status)
        .alias(alias)
        .ticketUrl(ticketUrl)
        .stop(stop)
        .build()
        .withDeployment(
            new Deployment(
                1,
                List.of(Deployment.Target.of("proj", "dev").withMerge(merge).withGreen()),
                1,
                askedDigest));
  }
}
