package dev.jagt.orchestrator.service.reach;

import dev.jagt.orchestrator.task.TaskState;
import org.jspecify.annotations.Nullable;

/** Reads now why a task's session cannot start; null where nothing holds it. */
@FunctionalInterface
public interface SessionVet {

  @Nullable String vet(TaskState task);
}
