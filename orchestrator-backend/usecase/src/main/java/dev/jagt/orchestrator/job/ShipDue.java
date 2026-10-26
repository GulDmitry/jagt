package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.ReviewDrafts;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A ship worth pressing unasked: the task holds something new to ship, and one more ship trips no loop stop. */
@Component
@RequiredArgsConstructor
class ShipDue {

    private final WorktreeChanges changes;
    private final ReviewDrafts drafts;
    private final TaskStops stops;

    boolean due(String taskId, TaskState task) {
        return (changes.anythingNewToShip(task) || drafts.drafted(task)) && stops.mayShip(taskId);
    }
}
