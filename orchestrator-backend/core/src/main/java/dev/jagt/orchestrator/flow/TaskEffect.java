package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStatus;

/** What a chart step does to a task, as data; {@link TaskReducer} is the one place that applies it. */
sealed interface TaskEffect {

    record Lands(TaskStatus to) implements TaskEffect {
    }

    record ClearsConflict() implements TaskEffect {
    }
}
