package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.WorktreeChanges;
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
        content.of(task).ifPresent(shipped -> turns.mark(taskId, TaskTurns.PRESSED_SHIP, shipped));
        changes.state(task).ifPresent(tree -> turns.shippedOn(taskId, tree));
    }
}
