package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** What the Master presses on a ready round. Closing a task is the human's alone: a round holding nothing waits for them. */
@Service
@RequiredArgsConstructor
public class MasterShip {

    private final WorktreeChanges changes;
    private final MasterPress press;
    private final MasterOpen opener;

    /** False where nothing was pressed. */
    public boolean ship(String taskId, TaskState task) {
        return changes.anyToShip(task) && press.press(taskId, task, TaskAction.SHIP);
    }

    public String open(String line) {
        return opener.open(line);
    }
}
