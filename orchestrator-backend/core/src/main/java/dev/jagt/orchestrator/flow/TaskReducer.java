package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** The one place a task's status is written: the chart's step from the task's status, every effect applied in order. */
final class TaskReducer {

    private TaskReducer() {
    }

    /** {@code move} applied to {@code task}; where the chart has no edge for it the status stays. */
    static TaskState reduce(TaskState task, TaskMove move, @Nullable String message, boolean event, long now) {
        List<TaskEffect> effects = TaskChart.effects(task.status(), move);
        if (effects.isEmpty()) {
            return stayed(task, message, event, now);
        }
        TaskState moved = task;
        for (TaskEffect effect : effects) {
            moved = switch (effect) {
                case TaskEffect.Lands lands -> moved.withStatus(lands.to(), message, event, now);
                case TaskEffect.ClearsConflict cleared -> moved.withoutConflict();
            };
        }
        return moved;
    }

    /** A line or a step written on the status the task already has, no move taken. */
    static TaskState stayed(TaskState task, @Nullable String message, boolean event, long now) {
        return task.withStatus(task.status(), message, event, now);
    }
}
