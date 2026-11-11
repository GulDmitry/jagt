package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;

/**
 * One thing that can be done to one task. It knows no status: it does the work and reports an
 * {@link Outcome}, and what that means for the task is the flow table's answer.
 */
public interface TaskCapability {

  TaskAction action();

  /** Highest wins when two declare the same action. */
  default int priority() {
    return 0;
  }

  /** Whether the configuration lets this verb be taken at all, whatever the task. */
  default boolean offered() {
    return true;
  }

  Outcome run(String taskId);

  /** The work on the project a human named, once vetted. */
  default Outcome run(String taskId, String project) {
    throw Refusal.byState(action().id() + " takes no project");
  }

  /** The work on the line a human typed, once vetted. */
  default Outcome runLine(String taskId, String line) {
    throw Refusal.byState(action().id() + " takes no line");
  }

  /** Throws a {@link Refusal} when the line cannot be taken, before anything is written. */
  default void vetLine(String line) {
    throw Refusal.byState(action().id() + " takes no line");
  }

  /**
   * Throws a {@link Refusal} when the project a human named cannot be taken, before anything is
   * written.
   */
  default void vet(String project) {
    throw Refusal.byState(action().id() + " takes no project");
  }

  /** As {@link #vet(String)}, for what the task holds. */
  default void vet(String taskId, TaskState task, String project) {
    vet(project);
  }

  /** What the rules read a task as when a human named a project for this verb. */
  default Facts naming(Facts facts) {
    return facts;
  }
}
