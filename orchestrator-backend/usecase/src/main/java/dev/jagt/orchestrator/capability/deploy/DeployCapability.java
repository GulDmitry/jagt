package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
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
