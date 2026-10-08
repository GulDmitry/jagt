package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Predicate;

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

    /** The status a report found the task in, and the one it left it in. */
    public record Landed(TaskStatus previous, TaskStatus now) {
    }

    public boolean report(String taskId, TaskStatus status, String message) {
        return report(taskId, status, message, (was, next) -> next).isPresent();
    }

    /**
     * The same, plus whatever else the report established. Applied in the SAME write, because a status that refuses
     * to exist without its link must not be able to land first; {@code alsoRecord} is handed the status BEFORE it.
     */
    public Optional<Landed> report(String taskId, TaskStatus status, String message,
                                   BiFunction<TaskStatus, TaskState, TaskState> alsoRecord) {
        return report(taskId, status, message, alsoRecord, task -> false);
    }

    /**
     * The same, told per task whether a hand-back still owes jagt a verification run. Asked of the state being
     * written, so two reports arriving together cannot disagree about it.
     */
    public Optional<Landed> report(String taskId, TaskStatus status, String message,
                                   BiFunction<TaskStatus, TaskState, TaskState> alsoRecord,
                                   Predicate<TaskState> verificationOwed) {
        if (!FlowRules.reportable(status)) {
            throw new IllegalArgumentException(FlowRules.refusedReport(status, status).orElseThrow());
        }
        // Judged against the state being WRITTEN, not one read a moment earlier: two reports arriving together
        // must not both pass on a status neither of them ends up leaving from.
        AtomicReference<Landed> landed = new AtomicReference<>();
        tasks.updateTask(taskId, task -> {
            FlowRules.refusedReport(task.status(), status).ifPresent(why -> {
                throw new IllegalArgumentException(why);
            });
            TaskStatus now = FlowRules.reported(task.status(), status, verificationOwed.test(task));
            landed.set(new Landed(task.status(), now));
            return alsoRecord.apply(task.status(), task.withStatus(now, message));
        });
        return Optional.ofNullable(landed.get());
    }

    /**
     * What jagt read off the task's review round on the host, the one way into a verdict. A read that moves nothing
     * writes nothing: a polled round reads the same every interval.
     */
    public Optional<Landed> read(String taskId, TaskStatus concluded, String message) {
        AtomicReference<Landed> landed = new AtomicReference<>();
        tasks.updateTask(taskId, task -> {
            TaskStatus now = FlowRules.readLands(task.status(), concluded);
            landed.set(new Landed(task.status(), now));
            return now == task.status() ? task : task.withStatus(now, message);
        });
        return Optional.ofNullable(landed.get());
    }
}
