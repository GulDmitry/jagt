package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.LoopGuard;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A ship worth pressing unasked: the task holds work, and one more ship trips no loop stop. */
@Component
@RequiredArgsConstructor
class ShipDue {

    private final WorktreeChanges changes;
    private final LoopGuard loops;

    boolean due(String taskId, TaskState task) {
        return changes.anyToShip(task) && loops.mayShip(taskId);
    }
}
