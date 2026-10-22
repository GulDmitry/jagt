package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.CapabilityInterceptor;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.LoopGuard;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.ActionOrigin;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/** Refuses {@code action} on a task jagt stopped as a loop, unless a human pressed it. */
public record LoopStopGuard(TaskAction action, LoopGuard loops) implements CapabilityInterceptor {

    private static final Set<ActionOrigin> HUMAN = EnumSet.of(ActionOrigin.BOARD, ActionOrigin.PALETTE);

    @Override
    public int order() {
        return -1;
    }

    @Override
    public Outcome around(String taskId, Supplier<Outcome> work) {
        if (!HUMAN.contains(OriginContext.current()) && loops.stopped(taskId)) {
            throw Refusal.byState(action.id() + " " + taskId + " refused: jagt stopped it as a suspected loop;"
                    + " only a human presses it until `lift`");
        }
        return work.get();
    }
}
