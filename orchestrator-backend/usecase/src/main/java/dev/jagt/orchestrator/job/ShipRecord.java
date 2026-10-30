package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The tree a ship was pressed on, kept on the task's turn; none where git could not read it. */
@Component
@RequiredArgsConstructor
class ShipRecord {

    private final TaskTurns turns;
    private final WorktreeChanges changes;

    void pressedOn(String taskId, TaskState task) {
        changes.state(task).ifPresent(tree -> turns.mark(taskId, TaskTurns.PRESSED_SHIP, tree));
    }
}
