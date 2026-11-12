package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Map;
import lombok.With;

@With
public record TestFinishedTaskBuilder(
    String id, long finishedAt, FinishedTask.Spend spend, TaskStatus status) {

  public static TestFinishedTaskBuilder finishedTask() {
    return new TestFinishedTaskBuilder(
        "ABC-1", 9L, new FinishedTask.Spend(0, null, null, null), TaskStatus.DEPLOYED);
  }

  public FinishedTask build() {
    return new FinishedTask(
        new FinishedTask.Identity(id, null, null, List.of(), null),
        finishedAt,
        spend,
        new FinishedTask.Course(
            List.of(new StatusChange(status, 1L, null)), 0, null, Map.of(), List.of(), null),
        List.of());
  }
}
