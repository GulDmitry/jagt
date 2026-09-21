package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.port.TaskCapability;
import dev.jagt.orchestrator.service.FinishedTasks;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DoneCapability implements TaskCapability {

    private final TaskRetirement retirement;
    private final FinishedTasks finished;

    @Override
    public TaskAction action() {
        return TaskAction.DONE;
    }

    @Override
    public Outcome run(String taskId) {
        // Before retirement: it drops the state entry, and the record is built from it.
        finished.record(taskId);
        return Outcome.gone(retirement.retire(taskId));
    }
}
