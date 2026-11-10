package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;
import java.util.function.BiFunction;

/** A {@link TaskStore} that changes a task with the reducer that writes its fields. */
public interface ReducingStore extends TaskStore {

  /** As {@link #updateTask}, handed the reducer. */
  boolean reduce(String taskId, BiFunction<TaskReducer, TaskState, TaskState> update);
}
