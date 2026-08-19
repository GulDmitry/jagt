package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.TaskStore;

import java.util.function.UnaryOperator;

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
        return report(taskId, status, message, UnaryOperator.identity());
    }

    /**
     * The same, plus whatever else the report established — a request link, the start of a polling window. Applied
     * in the SAME write, because a status that refuses to exist without its link must not be able to land first.
     */
    public boolean report(String taskId, TaskStatus status, String message, UnaryOperator<TaskState> alsoRecord) {
        if (!FlowRules.reportable(status)) {
            throw new IllegalArgumentException(status + " is jagt's to set, not a task's to report");
        }
        // Judged against the state being WRITTEN, not one read a moment earlier: two reports arriving together
        // must not both pass on a status neither of them ends up leaving from.
        return tasks.updateTask(taskId, task -> {
            if (!FlowRules.reportable(task.status(), status)) {
                throw new IllegalArgumentException(status + " cannot be reported by a task that is already "
                        + task.status() + " — that would take it backwards and start polling finished work");
            }
            return alsoRecord.apply(task.withStatus(status, message));
        });
    }
}
