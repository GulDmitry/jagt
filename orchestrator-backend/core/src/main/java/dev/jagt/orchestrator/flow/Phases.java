package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;

import java.util.Map;

/** Where each of the chart's regions stands for a task, read from the record alone. */
record Phases(TaskStatus lifecycle, StopKind stop, PlanGate plan, AgentReport report, TurnKind turn,
              DeployPhase deploy, SessionPhase session, GitPhase git, RepoPhase repo, RequestPhase request,
              ChecksPhase checks, ApprovalPhase approval, InfraPhase infra, Map<String, CheckerPhase> checkers,
              MasterPhase master, Map<String, CheckerPhase> beneath, boolean stampedAsk) {

    static Phases at(TaskStatus lifecycle) {
        return new Phases(lifecycle, StopKind.NONE, PlanGate.OPEN, AgentReport.PLAIN, TurnKind.CLOSED,
                DeployPhase.NONE, SessionPhase.NONE, GitPhase.UNREAD, RepoPhase.NONE, RequestPhase.NONE,
                ChecksPhase.NONE, ApprovalPhase.UNREAD, InfraPhase.NONE, Map.of(), MasterPhase.STANDS_IN, Map.of(), false);
    }

    static Phases of(TaskState task) {
        return new Phases(task.status(), StopKind.of(task.stop()), task.planGate(), task.report(),
                TurnKind.of(task.turnOrNone()), DeployPhase.of(task.deployment()), SessionPhase.of(task.session()),
                GitPhase.of(task.gitTrust()), RepoPhase.of(task.repoRequest()),
                RequestPhase.of(task),
                ChecksPhase.of(task), ApprovalPhase.of(task), InfraPhase.of(task),
                Map.of(CheckerRegion.QA.id(), CheckerPhase.of(task)), MasterPhase.of(task),
                Map.of(CheckerRegion.QA.id(), CheckerPhase.beneath(task)), task.session().stampedAsk());
    }

    CheckerPhase checker(String id) {
        return checkers.getOrDefault(id, CheckerPhase.NONE);
    }

    ChartFacts facts() {
        return new ChartFacts(stampedAsk, beneath, Facts.defaults());
    }
}
