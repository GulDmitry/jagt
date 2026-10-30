package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** The one place a task's status, stop, plan gate, report, turn and deployment are written: the chart's step, every effect applied in order. */
public final class TaskReducer {

    private TaskReducer() {
    }

    /** {@code move} applied to {@code task}; where the chart has no edge for it the status stays. */
    static TaskState reduce(TaskState task, TaskMove move, @Nullable String message, boolean event, long now) {
        List<TaskEffect> effects = effectsOf(task, move);
        if (effects.isEmpty()) {
            return stayed(task, message, event, now);
        }
        TaskState moved = task;
        for (TaskEffect effect : effects) {
            moved = apply(moved, effect, message, event, now);
        }
        return moved;
    }

    /** A line or a step written on the status the task already has, no move taken. */
    static TaskState stayed(TaskState task, @Nullable String message, boolean event, long now) {
        return task.withStatus(task.status(), message, event, now);
    }

    /** {@code task} stopped by {@code stop}; where a stop already stands, it stands alone. */
    public static TaskState stop(TaskState task, TaskStop stop, long now) {
        return step(task, new TaskMove.StopSet(stop), now);
    }

    /** {@code task} with its stop lifted by a human. */
    public static TaskState lift(TaskState task, long now) {
        return step(task, new TaskMove.Lifted(), now);
    }

    /** {@code task} with the stop that ends on {@code ends} cleared, where it was counted on {@code request} or none. */
    public static TaskState ended(TaskState task, TaskStop.Trigger.Ends ends, String request, long now) {
        TaskStop standing = task.stop();
        return standing == null || !standing.countedOnOrNone(request) ? task
                : step(task, new TaskMove.StopEnded(ends), now);
    }

    /** {@code task} asked to open in plan mode; where it is not plan mode, or the code is written, the gate stays. */
    public static TaskState planAsked(TaskState task, boolean planMode) {
        return step(task, new TaskMove.PlanAsked(planMode), 0);
    }

    /** {@code task} with the plan the human approved, its gate open. */
    public static TaskState planApproved(TaskState task) {
        return step(task, new TaskMove.PlanApproved(), 0);
    }

    /** {@code task} whose session declared it needs no plan; a gate the human asked for stays. */
    public static TaskState noPlanDeclared(TaskState task) {
        return step(task, new TaskMove.NoPlanDeclared(), 0);
    }

    /** {@code task} born under autoplan: its gate shut until a plan is approved or declared needless. */
    public static TaskState autoplanned(TaskState task) {
        return step(task, new TaskMove.Autoplanned(), 0);
    }

    /** {@code task} with the report a session made; a status that holds its line keeps a report that is not a question. */
    static TaskState said(TaskState task, AgentReport means) {
        return step(task, new TaskMove.Said(means), 0);
    }

    /** {@code task} with the report a verb's stamp means. */
    static TaskState stamped(TaskState task, AgentReport means) {
        return step(task, new TaskMove.Stamped(means), 0);
    }

    /** {@code task} whose session was handed the turn {@code step} opens; a turn already open is replaced. */
    public static TaskState relayed(TaskState task, String step, String digest, String answered, long now) {
        return step(task, new TaskMove.Relayed(step, digest, answered), now);
    }

    /** {@code task} whose turn a report closed, the next holder's starting at {@code now}. */
    public static TaskState turnClosed(TaskState task, long now) {
        return step(task, new TaskMove.TurnClosed(), now);
    }

    /** {@code task} with the turn {@code before} put back where a relay failed to send. */
    public static TaskState turnRestored(TaskState task, Turn before) {
        return step(task, new TaskMove.TurnRestored(before), 0);
    }

    /** {@code task} with {@code step} recorded on its deployment; a step the phase has no edge for is dropped. */
    public static TaskState deployStepped(TaskState task, DeployStep step, long now) {
        return step(task, new TaskMove.DeployStepped(step), now);
    }

    private static List<TaskEffect> effectsOf(TaskState task, TaskMove move) {
        return TaskChart.effects(task.status(), StopKind.of(task.stop()), task.planGate(), task.report(),
                TurnKind.of(task.turnOrNone()), DeployPhase.of(task.deployment()), move);
    }

    private static TaskState step(TaskState task, TaskMove move, long now) {
        TaskState moved = task;
        for (TaskEffect effect : effectsOf(task, move)) {
            moved = apply(moved, effect, null, false, now);
        }
        return moved;
    }

    private static TaskState apply(TaskState task, TaskEffect effect, @Nullable String message, boolean event,
                                   long now) {
        return switch (effect) {
            case TaskEffect.Lands lands -> task.withStatus(lands.to(), message, event, now);
            case TaskEffect.ClearsConflict cleared -> task.withoutConflict();
            case TaskEffect.Stops stops -> task.withStop(stops.stop().countedOn(task.requests()));
            case TaskEffect.ClearsStop cleared -> task.lifted(now);
            case TaskEffect.GatesPlan gates -> task.withPlanGate(gates.gate());
            case TaskEffect.Reports reports -> task.withReport(reports.report());
            case TaskEffect.OpensTurn opens -> task.withTurn(task.turnOrNone()
                    .relayed(opens.step(), opens.digest(), opens.answered(), now));
            case TaskEffect.ClosesTurn closes -> task.withTurn(task.turnOrNone().closed(now));
            case TaskEffect.RestoresTurn restores -> task.withTurn(restores.before());
            case TaskEffect.Deploys deploys -> task.withDeployment(deploys.step()
                    .applyTo(task.deployment() == null ? Deployment.defaults() : task.deployment(), now));
        };
    }
}
