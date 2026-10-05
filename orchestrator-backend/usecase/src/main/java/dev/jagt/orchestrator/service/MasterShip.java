package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** A ship the Master asks for. A ship of nothing moves no status, so the same verdict would ship again every tick. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterShip {

    private final WorktreeChanges changes;
    private final CommandService commands;

    /** False where no repository of the task holds anything to ship, and nothing was asked. */
    public boolean ship(String taskId, TaskState task) {
        if (!changes.anyToShip(task)) {
            return false;
        }
        log.atInfo().setMessage("master ships").addKeyValue("task", taskId).log();
        // Through the same door a human's press uses, so an illegal move is refused rather than taken, and
        // stamped as the Master's so what a ship does on its behalf can differ from what it does on yours.
        OriginContext.as(ActionOrigin.MASTER, () -> commands.execute(taskId, TaskAction.SHIP));
        return true;
    }
}
