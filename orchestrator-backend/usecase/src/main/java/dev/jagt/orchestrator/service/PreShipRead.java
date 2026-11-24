package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;

/** When a task's change is owed its read before the ship. */
public final class PreShipRead {

  private PreShipRead() {}

  public static boolean due(TaskState task) {
    return task.standing().status() == TaskStatus.REVIEW_PENDING
        && !task.hasReviewRequest()
        && !task.work().stopped()
        && !task.standing().report().asks()
        && task.worktreePath() != null;
  }
}
