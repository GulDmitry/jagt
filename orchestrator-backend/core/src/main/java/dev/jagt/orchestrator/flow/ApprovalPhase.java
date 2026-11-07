package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;

/**
 * The approval region's states: no read has said yet, approved on the head, or read not approved.
 */
public enum ApprovalPhase {
  UNREAD,
  APPROVED,
  WITHHELD;

  public static ApprovalPhase of(TaskState task) {
    if (task.approved() == null) {
      return UNREAD;
    }
    return task.approved() ? APPROVED : WITHHELD;
  }
}
