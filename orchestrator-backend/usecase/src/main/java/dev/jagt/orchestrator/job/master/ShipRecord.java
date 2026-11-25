package dev.jagt.orchestrator.job.master;

import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What a ship was pressed on, kept on the task's turn; none where git could not read the tree. */
@Component
@RequiredArgsConstructor
class ShipRecord {

  private final TaskTurns turns;
  private final WorktreeChanges changes;
  private final ShipContent content;

  void pressedOn(String taskId, TaskState task) {
    changes
        .state(task)
        .ifPresent(tree -> {
          turns.mark(taskId, TaskTurns.PRESSED_SHIP, content.with(task, tree));
          turns.shippedOn(taskId, tree);
        });
  }
}
