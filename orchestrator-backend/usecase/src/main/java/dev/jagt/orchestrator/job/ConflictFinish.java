package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.DeployStep;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

/** A resolution staged in full finishes the deploy the human pressed, as the Master, unless jagt stopped the task. */
@Component
@RequiredArgsConstructor
@Slf4j
class ConflictFinish {

    private final CommandService commands;
    private final TaskStops stops;
    private final StateService stateService;
    private final Clock clock;

    void deploy(String taskId, Path worktree) {
        Optional<TaskStop> stop = stateService.task(taskId).map(TaskState::stop);
        if (stop.isPresent()) {
            hold(taskId, stop.get());
            return;
        }
        if (!stops.moveDue(taskId)) {
            return;
        }
        try {
            String result = OriginContext.as(ActionOrigin.MASTER, () -> commands.execute(taskId, TaskAction.DEPLOY));
            log.atInfo().setMessage("master deploy pressed").addKeyValue("task", taskId)
                    .addKeyValue("worktree", worktree).addKeyValue("result", result).log();
        } catch (Refusal refusal) {
            log.atWarn().setMessage("master deploy refused").addKeyValue("task", taskId)
                    .addKeyValue("cause", refusal.getMessage()).addKeyValue("effect", "the deploy waits").log();
            return;
        }
    }

    private void hold(String taskId, TaskStop stop) {
        boolean said = stateService.task(taskId).flatMap(TaskState::currentConflict)
                .filter(conflict -> conflict.isHeldBy(stop.at())).isPresent();
        if (!said) {
            stateService.updateTask(taskId, task -> TaskReducer.deployStepped(task, new DeployStep.Held(stop.at()),
                    clock.millis()));
            log.atInfo().setMessage("conflict resolved held by stop").addKeyValue("task", taskId)
                    .addKeyValue("trigger", stop.trigger()).log();
        }
    }
}
