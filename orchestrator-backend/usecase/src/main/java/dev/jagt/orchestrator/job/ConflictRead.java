package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.ConflictEnding;
import dev.jagt.orchestrator.task.TaskState;

/** The read behind one {@link ConflictEnding}, run on a task standing in a deploy conflict. */
interface ConflictRead {

  ConflictEnding ending();

  /** Whether it may have changed the task, so a later read must see it again. */
  boolean read(String taskId, TaskState task);
}
