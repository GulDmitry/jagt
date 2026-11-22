package dev.jagt.orchestrator.service.task;

import dev.jagt.orchestrator.task.TaskState;
import java.util.Optional;

@FunctionalInterface
public interface Verdicts {

  Optional<String> said(TaskState task);
}
