package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;

/**
 * A checker region's states: nothing of the checker's on the deploy, a read running, a round out
 * the session has not settled, or a pass.
 */
public enum CheckerPhase {
  NONE,
  READING,
  ROUND_OPEN,
  PASSED;

  public static CheckerPhase of(TaskState task, String role) {
    return task.roleOrNone(role).reading().isEmpty() ? beneath(task, role) : READING;
  }

  /** What the deploy reads as were no read of the role running on it. */
  static CheckerPhase beneath(TaskState task, String role) {
    if (task.roundOpen(role)) {
      return ROUND_OPEN;
    }
    return task.passedOnTips(role) ? PASSED : NONE;
  }
}
