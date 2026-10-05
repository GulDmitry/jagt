package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** What the Master presses on a ready round. A ship of nothing moves no status, so it would ship again every tick. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterShip {

    private final WorktreeChanges changes;
    private final CommandService commands;

    /** A task holding nothing to ship is closed where {@code mayClose}; false where nothing was pressed. */
    public boolean ship(String taskId, TaskState task, boolean mayClose) {
        if (!changes.anyToShip(task)) {
            return mayClose && close(taskId);
        }
        log.atInfo().setMessage("master ships").addKeyValue("task", taskId).log();
        // Through the same door a human's press uses, so an illegal move is refused rather than taken, and
        // stamped as the Master's so what a ship does on its behalf can differ from what it does on yours.
        OriginContext.as(ActionOrigin.MASTER, () -> commands.execute(taskId, TaskAction.SHIP));
        return true;
    }

    private boolean close(String taskId) {
        log.atInfo().setMessage("master closes").addKeyValue("task", taskId)
                .addKeyValue("cause", "a ready round holds nothing to ship").log();
        OriginContext.as(ActionOrigin.MASTER, () -> commands.execute(taskId, TaskAction.DONE));
        return true;
    }
}
