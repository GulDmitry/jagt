package dev.jagt.orchestrator.flow.session;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.jspecify.annotations.Nullable;

/** Where a task stands: its lifecycle status, the way its stop ends, and the turn it holds. */
public record Standing(@Nullable TaskStatus status, String stop, long turn, long since) {

  /** What a task that is not held stands at. */
  public static final Standing ABSENT = new Standing(null, "", 0, 0);

  public static Standing of(TaskState task) {
    return new Standing(
        task.standing().status(),
        StopKind.of(task.work().stop()).name(),
        task.work().turnOrNone().id(),
        task.work().turnOrNone().since());
  }
}
