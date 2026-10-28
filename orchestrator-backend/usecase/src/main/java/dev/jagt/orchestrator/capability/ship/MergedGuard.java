package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.flow.CapabilityInterceptor;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.MergedRequests;

import java.util.function.Supplier;

/** Ships nothing once git finds every request of the task merged: the press records no step, so no loop counts it. */
public record MergedGuard(MergedRequests merged) implements CapabilityInterceptor {

    @Override
    public TaskAction action() {
        return TaskAction.SHIP;
    }

    @Override
    public int order() {
        return -1;
    }

    @Override
    public Outcome around(String taskId, Supplier<Outcome> work) {
        return merged.refresh(taskId)
                ? Outcome.nothing("ship " + taskId + ": every request is already merged — nothing to ship")
                : work.get();
    }
}
