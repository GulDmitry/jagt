package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import java.util.List;
import lombok.With;

@With
public record TestConflictedTaskBuilder(Deployment.Cause cause, String worktree) {

  public static TestConflictedTaskBuilder conflictedTask() {
    return new TestConflictedTaskBuilder(Deployment.Cause.MERGE, "/wt");
  }

  public TaskState build() {
    TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {});
    TaskState conflicted =
        TaskState.builder("web", worktree, TaskStatus.DEPLOY_CONFLICT)
            .lastActiveTimestamp(1_000)
            .build();
    TaskState opened =
        reducer.deployStepped(
            conflicted, new DeployStep.Opened(List.of(Deployment.Target.of("web", "dev"))), 1_000);
    return reducer.deployStepped(opened, new DeployStep.Conflicted("web", cause), 1_000);
  }
}
