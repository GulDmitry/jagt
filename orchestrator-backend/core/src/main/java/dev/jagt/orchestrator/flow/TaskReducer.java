package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** The one place a task's status and stop are written: the chart's step, every effect applied in order. */
public final class TaskReducer {

    private TaskReducer() {
    }

    /** {@code move} applied to {@code task}; where the chart has no edge for it the status stays. */
    static TaskState reduce(TaskState task, TaskMove move, @Nullable String message, boolean event, long now) {
        List<TaskEffect> effects = TaskChart.effects(task.status(), StopKind.of(task.stop()), move);
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
        return stopStep(task, new TaskMove.StopSet(stop), now);
    }

    /** {@code task} with its stop lifted by a human. */
    public static TaskState lift(TaskState task, long now) {
        return stopStep(task, new TaskMove.Lifted(), now);
    }

    /** {@code task} with the stop that ends on {@code ends} cleared, where it was counted on {@code request} or none. */
    public static TaskState ended(TaskState task, TaskStop.Trigger.Ends ends, String request, long now) {
        TaskStop standing = task.stop();
        return standing == null || !standing.countedOnOrNone(request) ? task
                : stopStep(task, new TaskMove.StopEnded(ends), now);
    }

    private static TaskState stopStep(TaskState task, TaskMove move, long now) {
        TaskState moved = task;
        for (TaskEffect effect : TaskChart.effects(task.status(), StopKind.of(task.stop()), move)) {
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
        };
    }
}
