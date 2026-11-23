package dev.jagt.orchestrator.flow.deploy;

import dev.jagt.orchestrator.task.TaskState;

/**
 * The approval region's states: no read has said yet, approved on the head, or read not approved.
 */
public enum ApprovalPhase {
  UNREAD,
  APPROVED,
  WITHHELD;

  public static ApprovalPhase of(TaskState task) {
    if (task.round().approved() == null) {
      return UNREAD;
    }
    return task.round().approved() ? APPROVED : WITHHELD;
  }
}
