package dev.jagt.orchestrator.capability.code;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CodeCapability implements TaskCapability {

  private final CodeLauncher ide;

  @Override
  public TaskAction action() {
    return TaskAction.CODE;
  }

  @Override
  public Outcome run(String taskId) {
    return Outcome.ok(ide.open(taskId, CodeLauncher.Mode.PROJECT));
  }
}
