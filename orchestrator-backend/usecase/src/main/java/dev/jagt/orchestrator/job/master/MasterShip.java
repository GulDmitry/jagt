package dev.jagt.orchestrator.job.master;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * What the Master presses on a ready round or in answer to a question. Closing a task is the
 * human's alone: a round holding nothing waits for them.
 */
@Service
@RequiredArgsConstructor
public class MasterShip {

  private final ShipDue due;
  private final MasterPress press;
  private final ShipRecord record;
  private final ShipContent content;

  /** False where nothing was pressed. */
  public boolean ship(String taskId, TaskState task) {
    if (!due.due(taskId, task) || !press.press(taskId, task, TaskAction.SHIP)) {
      return false;
    }
    record.pressedOn(taskId, task);
    return true;
  }

  /**
   * What a press of {@code action} would deliver as the tree stands; empty where git cannot say.
   */
  public Optional<String> standing(TaskState task) {
    return content.of(task);
  }

  /** A ship as {@link #ship} does, any other verb as a human's press; false where refused. */
  public boolean press(String taskId, TaskState task, TaskAction action) {
    return action == TaskAction.SHIP ? ship(taskId, task) : press.press(taskId, task, action);
  }
}
