package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.model.TaskAction;

/**
 * One thing that can be done to one task. It knows no status: it does the work and reports an {@link Outcome},
 * and what that means for the task is the flow table's answer. Replacing a built-in is therefore declaring
 * another one for the same action with a higher {@link #priority()}.
 */
public interface TaskCapability {

    TaskAction action();

    /** Highest wins when two declare the same action, so an install can replace one without patching jagt. */
    default int priority() {
        return 0;
    }

    Outcome run(String taskId);
}
