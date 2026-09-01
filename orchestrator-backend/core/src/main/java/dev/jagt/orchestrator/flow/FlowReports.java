package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;

import java.util.function.BiFunction;

/**
 * The second door into the machine: a status the task itself reports, rather than one an action led to. Every
 * report comes through here, so the machine has no entrance without a rule on it — a task cannot talk itself onto
 * a shared branch, out of one, or closed.
 */
public class FlowReports {

    public FlowReports(TaskStore tasks) {
        this.tasks = tasks;
    }

    private final TaskStore tasks;

    public boolean report(String taskId, TaskStatus status, String message) {
        return report(taskId, status, message, (was, next) -> next);
    }

    /**
     * The same, plus whatever else the report established. Applied in the SAME write, because a status that refuses
     * to exist without its link must not be able to land first; {@code alsoRecord} is handed the status BEFORE it.
     */
    public boolean report(String taskId, TaskStatus status, String message,
                          BiFunction<TaskStatus, TaskState, TaskState> alsoRecord) {
        if (!FlowRules.reportable(status)) {
            throw new IllegalArgumentException(FlowRules.refusedReport(status, status).orElseThrow());
        }
        // Judged against the state being WRITTEN, not one read a moment earlier: two reports arriving together
        // must not both pass on a status neither of them ends up leaving from.
        return tasks.updateTask(taskId, task -> {
            FlowRules.refusedReport(task.status(), status).ifPresent(why -> {
                throw new IllegalArgumentException(why);
            });
            return alsoRecord.apply(task.status(),
                    task.withStatus(FlowRules.reported(task.status(), status), message));
        });
    }
}
