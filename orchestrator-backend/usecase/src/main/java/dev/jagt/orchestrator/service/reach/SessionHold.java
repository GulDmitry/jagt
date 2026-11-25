package dev.jagt.orchestrator.service.reach;

import dev.jagt.orchestrator.task.TaskState;
import org.jspecify.annotations.Nullable;

/** Why a task's session cannot start now, in one line; null where nothing holds it. */
@FunctionalInterface
public interface SessionHold {

  @Nullable String held(TaskState task);
}
