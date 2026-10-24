package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskCapability;
import dev.jagt.orchestrator.service.TaskStops;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LiftCapability implements TaskCapability {

    private final TaskStops stops;

    @Override
    public TaskAction action() {
        return TaskAction.LIFT;
    }

    @Override
    public Outcome run(String taskId) {
        stops.lift(taskId);
        return Outcome.ok("lift " + taskId + ": auto-review and the Master may move it again; its rounds count anew");
    }
}
