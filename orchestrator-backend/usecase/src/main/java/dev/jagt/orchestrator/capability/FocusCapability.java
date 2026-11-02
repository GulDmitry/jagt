package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.TaskCapability;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FocusCapability implements TaskCapability {

    private final TaskViewer viewer;

    @Override
    public TaskAction action() {
        return TaskAction.FOCUS;
    }

    @Override
    public Outcome run(String taskId) {
        return Outcome.ok(viewer.focus(taskId));
    }
}
