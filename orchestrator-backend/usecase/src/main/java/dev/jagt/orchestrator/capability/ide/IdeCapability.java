package dev.jagt.orchestrator.capability.ide;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdeCapability implements TaskCapability {

  private final IdeLauncher ide;

  @Override
  public TaskAction action() {
    return TaskAction.IDE;
  }

  @Override
  public Outcome run(String taskId) {
    return Outcome.ok(ide.open(taskId, IdeLauncher.Mode.PROJECT));
  }
}
