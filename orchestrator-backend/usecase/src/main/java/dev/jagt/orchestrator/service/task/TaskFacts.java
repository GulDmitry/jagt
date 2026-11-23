package dev.jagt.orchestrator.service.task;

import dev.jagt.orchestrator.task.TaskState;
import org.jspecify.annotations.Nullable;

/** What the board holds about a task that a session which lost its context cannot know. */
public final class TaskFacts {

  private TaskFacts() {}

  public static String of(@Nullable TaskState task) {
    if (task == null) {
      return "";
    }
    String asked =
        task.standing().report().asks()
            ? "; your open question: " + task.standing().message()
            : present(task.standing().message())
                ? "; your last report: " + task.standing().message()
                : "";
    return (present(task.ticket().title()) ? " — " + task.ticket().title() : "")
        + (present(task.ticket().ticketUrl()) ? " (" + task.ticket().ticketUrl() + ")" : "")
        + ". On the board: "
        + task.standing().status()
        + asked;
  }

  private static boolean present(@Nullable String value) {
    return value != null && !value.isBlank();
  }
}
