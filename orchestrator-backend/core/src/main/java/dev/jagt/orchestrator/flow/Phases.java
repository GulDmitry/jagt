package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.deploy.ApprovalPhase;
import dev.jagt.orchestrator.flow.deploy.ChecksPhase;
import dev.jagt.orchestrator.flow.deploy.DeployPhase;
import dev.jagt.orchestrator.flow.deploy.InfraPhase;
import dev.jagt.orchestrator.flow.repo.GitPhase;
import dev.jagt.orchestrator.flow.repo.RepoPhase;
import dev.jagt.orchestrator.flow.repo.RequestPhase;
import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.StopKind;
import dev.jagt.orchestrator.flow.session.TurnKind;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Where each of the chart's regions stands for a task, read from the record alone. */
record Phases(Progress progress, Delivery delivery, Gates gates, Watchers watchers) {

  record Progress(
      TaskStatus lifecycle,
      StopKind stop,
      PlanGate plan,
      AgentReport report,
      TurnKind turn,
      SessionPhase session) {}

  record Delivery(DeployPhase deploy, GitPhase git, RepoPhase repo, RequestPhase request) {}

  record Gates(ChecksPhase checks, ApprovalPhase approval, InfraPhase infra) {}

  record Watchers(
      Map<String, CheckerPhase> checkers,
      Map<String, CheckerPhase> beneath,
      MasterPhase master,
      boolean stampedAsk) {}

  static Phases at(TaskStatus lifecycle) {
    return at(lifecycle, PlanGate.OPEN);
  }

  static Phases at(TaskStatus lifecycle, PlanGate plan) {
    return new Phases(
        new Progress(
            lifecycle, StopKind.NONE, plan, AgentReport.PLAIN, TurnKind.CLOSED, SessionPhase.NONE),
        new Delivery(DeployPhase.NONE, GitPhase.UNREAD, RepoPhase.NONE, RequestPhase.NONE),
        new Gates(ChecksPhase.NONE, ApprovalPhase.UNREAD, InfraPhase.NONE),
        new Watchers(Map.of(), Map.of(), MasterPhase.STANDS_IN, false));
  }

  static Phases of(TaskState task) {
    return new Phases(
        new Progress(
            task.standing().status(),
            StopKind.of(task.work().stop()),
            task.gate().planGate(),
            task.standing().report(),
            TurnKind.of(task.work().turnOrNone()),
            SessionPhase.of(task.work().session())),
        new Delivery(
            DeployPhase.of(task.work().deployment()),
            GitPhase.of(task.gate().gitTrust()),
            RepoPhase.of(task.work().repoRequest()),
            RequestPhase.of(task)),
        new Gates(ChecksPhase.of(task), ApprovalPhase.of(task), InfraPhase.of(task)),
        new Watchers(
            phasesOf(task, CheckerPhase::of),
            phasesOf(task, CheckerPhase::beneath),
            MasterPhase.of(task),
            task.work().session().hasStampedAsk()));
  }

  private static Map<String, CheckerPhase> phasesOf(
      TaskState task, BiFunction<TaskState, String, CheckerPhase> phase) {
    return task.work().roles().keySet().stream()
        .collect(Collectors.toMap(Function.identity(), id -> phase.apply(task, id)));
  }

  CheckerPhase checker(String id) {
    return watchers.checkers().getOrDefault(id, CheckerPhase.NONE);
  }

  ChartFacts facts() {
    return new ChartFacts(
        watchers.stampedAsk(), watchers.beneath(), Facts.defaults(), ChartFacts.PROJECTED);
  }
}
