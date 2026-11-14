package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShipCapability implements TaskCapability {

  private final ShipService ships;

  @Override
  public TaskAction action() {
    return TaskAction.SHIP;
  }

  @Override
  public Outcome run(String taskId) {
    return ships.ship(taskId);
  }
}
