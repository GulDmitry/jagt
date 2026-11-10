package dev.jagt.orchestrator.task;

import java.util.List;
import java.util.Map;

final class ReachedTasks {

  private ReachedTasks() {}

  static FinishedTask reaching(
      String id, TaskStatus status, long statusAt, long finishedAt, FinishedTask.Spend spend) {
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
