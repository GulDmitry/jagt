package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.ShipService;
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
