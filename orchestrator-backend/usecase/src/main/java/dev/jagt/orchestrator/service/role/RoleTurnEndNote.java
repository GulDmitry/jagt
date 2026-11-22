package dev.jagt.orchestrator.service.role;

import dev.jagt.orchestrator.task.TaskState;
import java.util.Optional;

/** The line a role adds to an agent's turn end where the task is with it; none where it is not. */
public interface RoleTurnEndNote {

  Optional<String> noteFor(TaskState task);
}
