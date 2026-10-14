package dev.jagt.orchestrator.capability.trust;

import dev.jagt.orchestrator.flow.CapabilityInterceptor;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.TrustedGitState;

import java.util.function.Supplier;

/** Refuses {@code action} while what the task's git obeys is not what a human trusted. */
public record GitStateGuard(TaskAction action, TrustedGitState trusted) implements CapabilityInterceptor {

    @Override
    public int order() {
        return -1;
    }

    @Override
    public Outcome around(String taskId, Supplier<Outcome> work) {
        trusted.distrust(taskId).ifPresent(why -> {
            throw Refusal.byState(action.id() + " " + taskId + " refused: " + why);
        });
        return work.get();
    }
}
