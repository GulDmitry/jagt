package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.task.TaskState;

/** When a task's change is owed its read before the ship. */
public final class PreShipRead {

  private PreShipRead() {}

  public static boolean due(TaskState task) {
    return FlowRules.awaitsReview(task.standing().status())
        && !task.work().stopped()
        && !task.standing().report().asks()
        && task.worktreePath() != null;
  }
}
