package dev.jagt.orchestrator.flow;

/**
 * One thing that can be done to one task. It knows no status: it does the work and reports an {@link Outcome}, and
 * what that means for the task is the flow table's answer.
 */
public interface TaskCapability {

    TaskAction action();

    /** Highest wins when two declare the same action. */
    default int priority() {
        return 0;
    }

    Outcome run(String taskId);
}
