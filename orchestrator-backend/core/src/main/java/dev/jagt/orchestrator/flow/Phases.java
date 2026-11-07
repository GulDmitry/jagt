package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Where each of the chart's regions stands for a task, read from the record alone. */
record Phases(Progress progress, Delivery delivery, Gates gates, Watchers watchers) {

    record Progress(TaskStatus lifecycle, StopKind stop, PlanGate plan, AgentReport report, TurnKind turn,
                    SessionPhase session) {
    }

    record Delivery(DeployPhase deploy, GitPhase git, RepoPhase repo, RequestPhase request) {
    }

    record Gates(ChecksPhase checks, ApprovalPhase approval, InfraPhase infra) {
    }

    record Watchers(Map<String, CheckerPhase> checkers, Map<String, CheckerPhase> beneath, MasterPhase master,
                    boolean stampedAsk) {
    }

    static Phases at(TaskStatus lifecycle) {
        return new Phases(
                new Progress(lifecycle, StopKind.NONE, PlanGate.OPEN, AgentReport.PLAIN, TurnKind.CLOSED,
                        SessionPhase.NONE),
                new Delivery(DeployPhase.NONE, GitPhase.UNREAD, RepoPhase.NONE, RequestPhase.NONE),
                new Gates(ChecksPhase.NONE, ApprovalPhase.UNREAD, InfraPhase.NONE),
                new Watchers(Map.of(), Map.of(), MasterPhase.STANDS_IN, false));
    }

    static Phases of(TaskState task) {
        return new Phases(
                new Progress(task.status(), StopKind.of(task.stop()), task.planGate(), task.report(),
                        TurnKind.of(task.turnOrNone()), SessionPhase.of(task.session())),
                new Delivery(DeployPhase.of(task.deployment()), GitPhase.of(task.gitTrust()),
                        RepoPhase.of(task.repoRequest()), RequestPhase.of(task)),
                new Gates(ChecksPhase.of(task), ApprovalPhase.of(task), InfraPhase.of(task)),
                new Watchers(phasesOf(task, CheckerPhase::of), phasesOf(task, CheckerPhase::beneath),
                        MasterPhase.of(task), task.session().stampedAsk()));
    }

    private static Map<String, CheckerPhase> phasesOf(TaskState task, BiFunction<TaskState, String, CheckerPhase> phase) {
        return task.roles().keySet().stream().collect(Collectors.toMap(Function.identity(), id -> phase.apply(task, id)));
    }

    CheckerPhase checker(String id) {
        return watchers.checkers().getOrDefault(id, CheckerPhase.NONE);
    }

    ChartFacts facts() {
        return new ChartFacts(watchers.stampedAsk(), watchers.beneath(), Facts.defaults());
    }
}
