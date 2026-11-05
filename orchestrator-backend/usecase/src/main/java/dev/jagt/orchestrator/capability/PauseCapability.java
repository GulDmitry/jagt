package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskCapability;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.TaskStop;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PauseCapability implements TaskCapability {

    private final TaskStops stops;

    @Override
    public TaskAction action() {
        return TaskAction.PAUSE;
    }

    @Override
    public Outcome run(String taskId) {
        stops.stop(taskId, TaskStop.Trigger.PAUSE, "you paused it");
        return Outcome.ok("pause " + taskId + ": nothing of jagt moves it until you lift the stop");
    }
}
