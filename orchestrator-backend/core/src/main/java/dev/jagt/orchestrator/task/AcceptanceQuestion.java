package dev.jagt.orchestrator.task;

import org.jspecify.annotations.Nullable;

/** The question acceptance leaves on a task for a criterion it could not prove. */
public final class AcceptanceQuestion {

  public static final String PREFIX = "Acceptance could not prove, on ";

  private AcceptanceQuestion() {}

  /** The question standing on {@code task}, null where none is acceptance's. */
  public static @Nullable String standing(TaskState task) {
    String asked = task.message();
    return task.report() == AgentReport.QUESTION && asked != null && asked.startsWith(PREFIX)
        ? asked
        : null;
  }
}
