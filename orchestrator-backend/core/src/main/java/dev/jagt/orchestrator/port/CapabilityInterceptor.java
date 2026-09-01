package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;

import java.util.function.Supplier;

/**
 * Something to do around one verb without touching it. Throwing from {@link #around} means the work does not run
 * and the task does not move; returning the supplier's own answer is the neutral thing to do.
 */
public interface CapabilityInterceptor {

    TaskAction action();

    /** Lowest first, so a check that should refuse before an expensive one says so. */
    default int order() {
        return 0;
    }

    Outcome around(String taskId, Supplier<Outcome> work);
}
