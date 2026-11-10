package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;

final class TaskWalk {

  private TaskWalk() {}

  static TaskState step(TaskReducer reducer, TaskState task, TaskMove raw) {
    return reducer.reduce(task, reducer.resolved(task, raw), "step", true, 1_000);
  }

  static TaskState lastOf(TaskState left, TaskState right) {
    return right;
  }
}
