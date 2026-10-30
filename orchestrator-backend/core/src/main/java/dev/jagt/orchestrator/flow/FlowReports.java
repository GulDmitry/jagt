package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.Guard;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * The second door into the machine: a status the task itself reports, rather than one an action led to. Every
 * report comes through here, so the machine has no entrance without a rule on it — a task cannot talk itself onto
 * a shared branch, out of one, or closed.
 */
@RequiredArgsConstructor
public class FlowReports {

    private final TaskStore tasks;
    private final Clock clock;

    /** The status a report found the task in, and the one it left it in. */
    public record Landed(TaskStatus previous, TaskStatus now) {
    }

    public boolean report(String taskId, TaskStatus status, @Nullable String message) {
        return report(taskId, status, AgentReport.PLAIN, message);
    }

    public boolean report(String taskId, TaskStatus status, AgentReport means, @Nullable String message) {
        return report(taskId, status, means, message, (was, next) -> next).isPresent();
    }

    /**
     * The same, plus whatever else the report established. Applied in the SAME write, because a status that refuses
     * to exist without its link must not be able to land first; {@code alsoRecord} is handed the status BEFORE it.
     */
    public Optional<Landed> report(String taskId, TaskStatus status, AgentReport means, @Nullable String message,
                                   BiFunction<TaskStatus, TaskState, TaskState> alsoRecord) {
        return report(taskId, status, means, message, alsoRecord, task -> false);
    }

    /**
     * The same, told per task whether a hand-back still owes jagt a verification run. Asked of the state being
     * written, so two reports arriving together cannot disagree about it.
     */
    public Optional<Landed> report(String taskId, TaskStatus status, AgentReport means, @Nullable String message,
                                   BiFunction<TaskStatus, TaskState, TaskState> alsoRecord,
                                   Predicate<TaskState> verificationOwed) {
        return report(taskId, status, means, message, alsoRecord, verificationOwed, task -> true);
    }

    /** What a report made while standing found: it landed, the task moved on, or the task is not held. */
    public enum Claimed { LANDED, MOVED_ON, GONE }

    /**
     * A report that lands only while the task still has the status and turn {@code read} had, judged in the write
     * itself, so nothing lands between the check and the status.
     */
    public Claimed reportWhileStanding(String taskId, TaskState read, TaskStatus status, AgentReport means,
                                       @Nullable String message, UnaryOperator<TaskState> alsoRecord) {
        AtomicReference<Boolean> moved = new AtomicReference<>(false);
        Predicate<TaskState> standing = task -> {
            boolean stands = standing(task, read);
            moved.set(!stands);
            return stands;
        };
        Optional<Landed> landed = report(taskId, status, means, message, (was, next) -> alsoRecord.apply(next),
                task -> false, standing);
        return landed.isPresent() ? Claimed.LANDED : moved.get() ? Claimed.MOVED_ON : Claimed.GONE;
    }

    /** The same claim for a write that moves no status. */
    public Claimed claimWhileStanding(String taskId, TaskState read, UnaryOperator<TaskState> write) {
        AtomicReference<Claimed> claimed = new AtomicReference<>(Claimed.GONE);
        tasks.updateTask(taskId, task -> {
            boolean stands = standing(task, read);
            claimed.set(stands ? Claimed.LANDED : Claimed.MOVED_ON);
            return stands ? write.apply(task) : task;
        });
        return claimed.get();
    }

    /**
     * Puts the task back as {@code read} had it and hands its turn to the human, while the turn is still the one the
     * claim opened; false where the task moved on since, which is left as it is.
     */
    public boolean undoClaim(String taskId, TaskState read, Turn claimed) {
        AtomicBoolean undone = new AtomicBoolean();
        tasks.updateTask(taskId, task -> {
            if (!task.turnOrNone().sameTurnAs(claimed)) {
                return task;
            }
            undone.set(true);
            long now = clock.millis();
            return TaskReducer.turnClosed(task.restoredFrom(read, now), now);
        });
        return undone.get();
    }

    /** Whether the task is still where it was read: the same {@link Standing}, no relay, report or stop since. */
    public boolean standing(String taskId, TaskState read) {
        return tasks.task(taskId).map(task -> standing(task, read)).orElse(true);
    }

    private static boolean standing(TaskState task, TaskState read) {
        return Standing.of(task).equals(Standing.of(read));
    }

    private Optional<Landed> report(String taskId, TaskStatus status, AgentReport means, @Nullable String message,
                                    BiFunction<TaskStatus, TaskState, TaskState> alsoRecord,
                                    Predicate<TaskState> verificationOwed, Predicate<TaskState> standing) {
        // Judged against the state being WRITTEN, not one read a moment earlier: two reports arriving together
        // must not both pass on a status neither of them ends up leaving from.
        AtomicReference<Landed> landed = new AtomicReference<>();
        AtomicReference<String> refused = new AtomicReference<>();
        tasks.updateTask(taskId, task -> {
            if (!standing.test(task)) {
                return task;
            }
            Optional<String> why = FlowRules.refusedReport(task.status(), status);
            if (why.isPresent()) {
                refused.set(why.get());
                return task.refused(Guard.STATUS_REPORT);
            }
            TaskMove.Reported move = new TaskMove.Reported(status, !means.asks() && verificationOwed.test(task));
            landed.set(new Landed(task.status(), FlowRules.reported(task.status(), status, move.verificationOwed())));
            TaskState said = TaskReducer.reduce(TaskReducer.said(task, means), move, lineOf(task, means, message),
                    false, clock.millis());
            return alsoRecord.apply(task.status(), said);
        });
        Optional<String> why = Optional.ofNullable(refused.get())
                .or(() -> FlowRules.reportable(status) ? Optional.empty() : FlowRules.refusedReport(status, status));
        if (why.isPresent()) {
            throw Refusal.byState(why.get());
        }
        return Optional.ofNullable(landed.get());
    }

    /** A question still reaches the card; once one is behind, the line falls back to the status's own. */
    private static @Nullable String lineOf(TaskState task, AgentReport means, @Nullable String message) {
        if (!FlowRules.holdsItsLine(task.status()) || means.asks()) {
            return message;
        }
        return task.report().asks() ? null : task.message();
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
            if (now == task.status()) {
                return task;
            }
            return TaskReducer.reduce(task, new TaskMove.Read(concluded),
                    FlowRules.standsThroughARead(task.report()) ? task.message() : message, false, clock.millis());
        });
        return Optional.ofNullable(landed.get());
    }

    /**
     * What jagt read of the pipeline on a deployed task's commit: the line the task carries while it is open, or
     * the deploy handed back for a red the code owns. A task that is not live on the deploy branch is left alone,
     * and so is one already saying it; false where nothing was written. {@code redProject} is the repository whose
     * pipeline is red on the code, null while none is; the handed-back deploy records it as conflicted for that cause.
     */
    public boolean readDeploy(String taskId, @Nullable String redProject, String message) {
        boolean redOnTheCode = redProject != null;
        AtomicBoolean written = new AtomicBoolean();
        tasks.updateTask(taskId, task -> {
            if (!FlowRules.checkedWhereItLanded(task.status())) {
                return task;
            }
            TaskStatus now = FlowRules.deployReadLands(task.status(), redOnTheCode);
            if (now == task.status() && message.equals(task.message())) {
                return task;
            }
            written.set(true);
            TaskState read = TaskReducer.reduce(task, new TaskMove.DeployRead(redOnTheCode), message,
                    now != task.status(), clock.millis());
            return redProject == null ? read : TaskReducer.deployStepped(read,
                    new DeployStep.Conflicted(redProject, Deployment.Cause.RED_PIPELINE), clock.millis());
        });
        return written.get();
    }

    /** The deploy's check recorded as asked on the facts of {@code digest}, so the session is not asked again. */
    /** Marks {@code bare} as the landed targets that settled with no pipeline listed; a target listed since is cleared. */
    public void deployBare(String taskId, List<String> bare) {
        tasks.updateTask(taskId, task -> TaskReducer.deployStepped(task, new DeployStep.Bare(bare), clock.millis()));
    }

    /** Counts a read that could not list {@code unread}'s pipeline, null where it listed every one; the reads running, at most. */
    public int deployReadOn(String taskId, @Nullable String unread) {
        tasks.updateTask(taskId, task -> TaskReducer.deployStepped(task, new DeployStep.ReadOn(unread), clock.millis()));
        return tasks.task(taskId).map(TaskState::deployment).map(deployment -> deployment.targets().stream()
                .mapToInt(Deployment.Target::unreadReads).max().orElse(0)).orElse(0);
    }

    public void deployAsked(String taskId, String digest) {
        tasks.updateTask(taskId, task -> TaskReducer.deployStepped(task, new DeployStep.Asked(digest), clock.millis()));
    }
}
