package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;

/** What a chart step does to a task, as data; {@link TaskReducer} is the one place that applies it. */
sealed interface TaskEffect {

    record Lands(TaskStatus to) implements TaskEffect {
    }

    record Stops(TaskStop stop) implements TaskEffect {
    }

    record ClearsStop() implements TaskEffect {
    }

    record GatesPlan(PlanGate gate) implements TaskEffect {
    }

    record Reports(AgentReport report) implements TaskEffect {
    }

    record OpensTurn(String step, String digest, String answered) implements TaskEffect {
    }

    record ClosesTurn() implements TaskEffect {
    }

    record RestoresTurn(Turn before) implements TaskEffect {
    }

    record Deploys(DeployStep step) implements TaskEffect {
    }
}
