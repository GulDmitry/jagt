package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.Deployment;
import java.util.List;
import lombok.With;

@With
public record TestDeploymentBuilder(List<Deployment.Target> targets, int at) {

  public static TestDeploymentBuilder deployment() {
    return new TestDeploymentBuilder(List.of(), 1);
  }

  public static TestDeploymentBuilder projectMergedAndConflictedBy(Deployment.Cause cause) {
    return new TestDeploymentBuilder(
        List.of(
            Deployment.Target.of("proj", "dev")
                .withMerge("cafebabe1234")
                .withConflict(Conflict.of(cause, 1))),
        0);
  }

  public static TestDeploymentBuilder apiMergedAndWebConflicted() {
    return new TestDeploymentBuilder(
        List.of(
            Deployment.Target.of("api", "dev").withMerge("cafebabe1234"),
            Deployment.Target.of("web", "dev")
                .withConflict(Conflict.of(Deployment.Cause.MERGE, 1))),
        1);
  }

  public Deployment build() {
    return new Deployment(1, targets, at, null);
  }
}
