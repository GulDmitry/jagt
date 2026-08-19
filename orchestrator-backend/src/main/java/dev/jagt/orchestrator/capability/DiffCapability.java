package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.service.IdeLauncher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiffCapability implements TaskCapability {

    private final IdeLauncher ide;

    @Override
    public TaskAction action() {
        return TaskAction.DIFF;
    }

    @Override
    public Outcome run(String taskId) {
        return Outcome.ok(ide.open(taskId, "diff"));
    }
}
