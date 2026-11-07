package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.TaskState;

/**
 * Whether a task's work stands where no request is opened, so nothing waits on a review and the
 * deploy is next.
 */
@FunctionalInterface
public interface RequestlessShip {

  boolean stands(TaskState task);
}
