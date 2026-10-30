package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;

/** What a chart step does to a task, as data; {@link TaskReducer} is the one place that applies it. */
sealed interface TaskEffect {

    record Lands(TaskStatus to) implements TaskEffect {
    }

    record ClearsConflict() implements TaskEffect {
    }

    record Stops(TaskStop stop) implements TaskEffect {
    }

    record ClearsStop() implements TaskEffect {
    }

    record GatesPlan(PlanGate gate) implements TaskEffect {
    }
}
