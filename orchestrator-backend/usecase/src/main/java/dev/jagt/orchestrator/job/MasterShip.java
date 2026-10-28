package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** What the Master presses on a ready round. Closing a task is the human's alone: a round holding nothing waits for them. */
@Service
@RequiredArgsConstructor
public class MasterShip {

    private final ShipDue due;
    private final MasterPress press;
    private final ShipRecord record;

    /** False where nothing was pressed. */
    public boolean ship(String taskId, TaskState task) {
        if (!due.due(taskId, task) || !press.press(taskId, task, TaskAction.SHIP)) {
            return false;
        }
        record.pressedOn(taskId, task);
        return true;
    }
}
