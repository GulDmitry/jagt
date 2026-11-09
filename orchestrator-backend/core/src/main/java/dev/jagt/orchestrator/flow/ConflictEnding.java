package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskState;
import java.util.List;

/** The reads that end a deploy conflict, each run while the conflict stands. */
public enum ConflictEnding {
  /** A pipeline on each held deploy branch, later than the red one and containing it, is green. */
  LATER_GREEN,
  /** The request of the repository the deploy was handed back on is closed on the host. */
  REQUEST_CLOSED,
  /** The resolution is staged in full in the conflict's worktree. */
  STAGED_FIX;

  public static List<ConflictEnding> of(Deployment.Cause cause) {
    return switch (cause) {
      case MERGE -> List.of(REQUEST_CLOSED, STAGED_FIX);
      case RED_PIPELINE -> List.of(LATER_GREEN, REQUEST_CLOSED, STAGED_FIX);
      case REVERT -> List.of(STAGED_FIX);
    };
  }

  /** The reads that may end the conflict {@code task} stands in now, in the order they run. */
  public static List<ConflictEnding> of(TaskState task) {
    return task.currentConflict().map(Conflict::cause).map(ConflictEnding::of).orElse(List.of());
  }

  public boolean readsTheHost() {
    return this != STAGED_FIX;
  }

  public boolean readsUnder(TaskState task) {
    StopKind stop = StopKind.of(task.stop());
    return switch (this) {
      case LATER_GREEN -> stop == StopKind.NONE || stop == StopKind.DEPLOY_READ;
      case REQUEST_CLOSED, STAGED_FIX -> stop != StopKind.PAUSE;
    };
  }
}
