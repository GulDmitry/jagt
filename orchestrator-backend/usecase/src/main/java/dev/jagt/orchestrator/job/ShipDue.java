package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.ReviewDrafts;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A ship worth pressing unasked: a request still open, something new to ship, and one more ship trips no loop stop.
 * A tree the last ship was pressed on is not new, whatever git still counts as uncommitted.
 */
@Component
@RequiredArgsConstructor
class ShipDue {

    private final WorktreeChanges changes;
    private final ReviewDrafts drafts;
    private final TaskStops stops;

    boolean due(String taskId, TaskState task) {
        return !task.requestsMerged() && !shippedAsItStands(task)
                && (changes.anythingNewToShip(task) || drafts.drafted(task)) && stops.mayShip(taskId);
    }

    private boolean shippedAsItStands(TaskState task) {
        return changes.state(task).filter(tree -> task.turnOrNone().marked(TaskTurns.PRESSED_SHIP, tree)).isPresent();
    }
}
