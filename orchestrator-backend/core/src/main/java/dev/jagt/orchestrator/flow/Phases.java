package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;

/** Where each of the chart's regions stands for a task, read from the record alone. */
record Phases(TaskStatus lifecycle, StopKind stop, PlanGate plan, AgentReport report, TurnKind turn,
              DeployPhase deploy, SessionPhase session, GitPhase git, RepoPhase repo, boolean stampedAsk) {

    static Phases at(TaskStatus lifecycle) {
        return new Phases(lifecycle, StopKind.NONE, PlanGate.OPEN, AgentReport.PLAIN, TurnKind.CLOSED,
                DeployPhase.NONE, SessionPhase.NONE, GitPhase.UNREAD, RepoPhase.NONE, false);
    }

    static Phases of(TaskState task) {
        return new Phases(task.status(), StopKind.of(task.stop()), task.planGate(), task.report(),
                TurnKind.of(task.turnOrNone()), DeployPhase.of(task.deployment()), SessionPhase.of(task.session()),
                GitPhase.of(task.gitTrust()), RepoPhase.of(task.repoRequest()), task.session().stampedAsk());
    }
}
