package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.port.TaskCapability;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.ReviewSweepService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A round decides nothing: what it found is reported and nothing more. */
@Component
@RequiredArgsConstructor
public class SweepCapability implements TaskCapability {

    private final ReviewSweepService sweeps;

    @Override
    public TaskAction action() {
        return TaskAction.SWEEP;
    }

    @Override
    public Outcome run(String taskId) {
        return Outcome.ok(sweeps.sweep(taskId).message());
    }
}
