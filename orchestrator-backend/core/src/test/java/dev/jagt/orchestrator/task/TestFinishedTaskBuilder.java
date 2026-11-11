package dev.jagt.orchestrator.task;

import java.util.List;
import java.util.Map;
import lombok.With;

@With
public record TestFinishedTaskBuilder(
    String id, TaskStatus status, long statusAt, long finishedAt, FinishedTask.Spend spend) {

  public static TestFinishedTaskBuilder finishedTask() {
    return new TestFinishedTaskBuilder(
        "ABC-1", TaskStatus.DEPLOYED, 5L, 10L, new FinishedTask.Spend(0, 4.0, null, null));
  }

  public FinishedTask build() {
    return new FinishedTask(
        new FinishedTask.Identity(id, null, null, List.of(), null),
        finishedAt,
        spend,
        new FinishedTask.Course(
            List.of(new StatusChange(status, statusAt, ActionOrigin.BOARD)),
            0,
            null,
            Map.of(),
            List.of(),
            null),
        List.of());
  }
}
