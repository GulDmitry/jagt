package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** What the Master presses on a ready round. Closing a task is the human's alone: a round holding nothing waits for them. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterShip {

    private final WorktreeChanges changes;
    private final CommandService commands;
    private final TaskLauncher launcher;

    /** False where nothing was pressed. */
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

    /** A part of the work needing a branch of its own, opened by the {@code do} line a human would type. */
    public String open(String line) {
        log.atInfo().setMessage("master opens a task").addKeyValue("line", line).log();
        return launcher.launchLine(line).message();
    }
}
