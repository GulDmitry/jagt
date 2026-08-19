package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.AgentSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FocusCapability implements TaskCapability {

    private final AgentSessions sessions;

    @Override
    public TaskAction action() {
        return TaskAction.FOCUS;
    }

    @Override
    public Outcome run(String taskId) {
        return Outcome.ok(sessions.focusTask(taskId));
    }
}
