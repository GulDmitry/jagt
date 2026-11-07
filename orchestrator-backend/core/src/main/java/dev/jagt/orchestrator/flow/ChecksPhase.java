package dev.jagt.orchestrator.flow;

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
    if (task.pipelineUnread()) {
      return UNREAD;
    }
    if (task.pipelineStatus() == null) {
      return NONE;
    }
    return task.checksRead() == null ? STALE : READ;
  }
}
