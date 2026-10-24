package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** A verb pressed as the human's own press would be, unless the task touched a path its project keeps for them. */
@Component
@RequiredArgsConstructor
@Slf4j
class MasterPress {

    private final CommandService commands;
    private final HumanPaths humanPaths;
    private final ConfigService config;
    private final TaskTurns turns;

    /** False where the press was left to the human. */
    boolean press(String taskId, TaskState task, TaskAction action) {
        Optional<String> touched = humanPaths.touched(task);
        String left = task.turnOrNone().mark(TaskTurns.LEFT);
        if (touched.isPresent()) {
            // A step left waiting is offered again every tick.
            String step = action.id() + " " + touched.get();
            if (!step.equals(left)) {
                turns.mark(taskId, TaskTurns.LEFT, step);
                log.atInfo().setMessage("master leaves it").addKeyValue("task", taskId)
                        .addKeyValue("cmd", action.id()).addKeyValue("path", touched.get()).log();
            }
            return false;
        }
        if (left != null) {
            turns.unmark(taskId, TaskTurns.LEFT);
        }
        execute(taskId, action);
        return true;
    }

    /** A verb no path of the task can keep from the human. */
    void execute(String taskId, TaskAction action) {
        log.atInfo().setMessage("master presses").addKeyValue("task", taskId).addKeyValue("cmd", action.id()).log();
        // Through the same door a human's press uses, so an illegal move is refused rather than taken, and
        // stamped as the Master's so what a verb does on its behalf can differ from what it does on yours.
        OriginContext.as(ActionOrigin.MASTER, () -> commands.execute(taskId, action));
        config.startTaken();
    }
}
