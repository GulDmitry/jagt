package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStatus;
import java.util.Optional;

@FunctionalInterface
interface Landing {

  Optional<TaskStatus> land(TaskStatus from, TaskMove move);
}
