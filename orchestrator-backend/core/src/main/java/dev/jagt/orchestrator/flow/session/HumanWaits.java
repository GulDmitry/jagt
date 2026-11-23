package dev.jagt.orchestrator.flow.session;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.HumanWait;
import dev.jagt.orchestrator.task.turn.HumanWait.Why;
import dev.jagt.orchestrator.task.turn.Turn.Holder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The intervals a task's turn rested with the human, kept on the task: one open at most, the last.
 */
public final class HumanWaits {

  public static final int MAX = 50;

  private HumanWaits() {}

  /**
   * The task with its waits brought in line with {@code holder}; the same instance where nothing
   * changed. {@code judged}: the Master has read the round or plan in front and left it to the
   * human.
   */
  public static TaskState noted(TaskState task, Holder holder, boolean judged, long now) {
    List<HumanWait> waits = new ArrayList<>(task.work().waits());
    Optional<HumanWait> open =
        waits.isEmpty() || !waits.getLast().open()
            ? Optional.empty()
            : Optional.of(waits.getLast());
    Optional<Why> why =
        Holder.HUMAN.equals(holder) ? Optional.of(whyOf(task, judged)) : Optional.empty();
    if (open.map(HumanWait::why).equals(why)) {
      return task;
    }
    open.ifPresent(wait -> waits.set(waits.size() - 1, wait.closedAt(now)));
    why.ifPresent(reason -> waits.add(new HumanWait(now, 0, reason)));
    return task.withWaits(waits.subList(Math.max(0, waits.size() - MAX), waits.size()));
  }

  /** The interval {@code after} ended that {@code before} still held open. */
  public static Optional<HumanWait> ended(TaskState before, TaskState after) {
    return before.work().waits().stream()
        .filter(HumanWait::open)
        .findFirst()
        .flatMap(open ->
            after.work().waits().stream()
                .filter(wait -> wait.since() == open.since() && !wait.open())
                .findFirst());
  }

  private static Why whyOf(TaskState task, boolean judged) {
    if (task.work().stopped()) {
      return Why.STOP;
    }
    if (task.standing().report() == AgentReport.PERMISSION) {
      return Why.PERMISSION;
    }
    if (task.standing().report() == AgentReport.QUESTION) {
      return Why.QUESTION;
    }
    return judged ? Why.VERDICT : Why.STATUS;
  }
}
