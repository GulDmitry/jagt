package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** A resolution staged in full finishes the deploy the human pressed, as the Master, unless jagt stopped the task. */
@Component
@RequiredArgsConstructor
@Slf4j
class ConflictFinish {

    private final CommandService commands;
    private final TaskStops stops;
    private final StateService stateService;
    /** Dedups a log line only: a restart says it once more. */
    private final Map<String, Long> heldBy = new ConcurrentHashMap<>();

    void deploy(String taskId, Path worktree) {
        Optional<TaskStop> stop = stateService.task(taskId).map(TaskState::stop);
        if (stop.isPresent()) {
            hold(taskId, stop.get());
            return;
        }
        heldBy.remove(taskId);
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
        if (!Long.valueOf(stop.at()).equals(heldBy.put(taskId, stop.at()))) {
            log.atInfo().setMessage("conflict resolved held by stop").addKeyValue("task", taskId)
                    .addKeyValue("trigger", stop.trigger()).log();
        }
    }
}
