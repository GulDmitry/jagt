package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.port.TaskCapability;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.TaskRetirement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DoneCapability implements TaskCapability {

    private final TaskRetirement retirement;

    @Override
    public TaskAction action() {
        return TaskAction.DONE;
    }

    @Override
    public Outcome run(String taskId) {
        return Outcome.gone(retirement.retire(taskId));
    }
}
