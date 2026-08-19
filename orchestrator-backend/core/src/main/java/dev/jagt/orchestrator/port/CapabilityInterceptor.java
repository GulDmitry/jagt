package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;

import java.util.function.Supplier;

/**
 * Something to do around one verb without touching it — a check before a deploy, a note after a ship. Declared per
 * action, so an install adds its own step by adding a class, and the verb it wraps never learns of it.
 *
 * <p>It may refuse: throwing from {@link #around} means the work does not run and the task does not move, which is
 * the point of a gate. Returning the supplier's own answer is the neutral thing to do.
 */
public interface CapabilityInterceptor {

    TaskAction action();

    /** Lowest first, so a check that should refuse before an expensive one says so. */
    default int order() {
        return 0;
    }

    Outcome around(String taskId, Supplier<Outcome> work);
}
