package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.DeployService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeployCapability implements TaskCapability {

    private final DeployService deploys;

    @Override
    public TaskAction action() {
        return TaskAction.DEPLOY;
    }

    @Override
    public Outcome run(String taskId) {
        return deploys.deploy(taskId);
    }
}
