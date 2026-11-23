package dev.jagt.orchestrator.flow.deploy;

import dev.jagt.orchestrator.task.TaskState;

/**
 * The checks region's states: none read, read on the head, read on another head, or the last round
 * unable to read them.
 */
public enum ChecksPhase {
  NONE,
  READ,
  STALE,
  UNREAD;

  public static ChecksPhase of(TaskState task) {
    if (task.checks().unread()) {
      return UNREAD;
    }
    if (task.checks().pipelineStatus() == null) {
      return NONE;
    }
    return task.checks().read() == null ? STALE : READ;
  }
}
