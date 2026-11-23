package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.deploy.ApprovalPhase;
import dev.jagt.orchestrator.flow.deploy.ChecksPhase;
import dev.jagt.orchestrator.flow.deploy.DeployPhase;
import dev.jagt.orchestrator.flow.deploy.InfraPhase;
import dev.jagt.orchestrator.flow.repo.GitPhase;
import dev.jagt.orchestrator.flow.repo.RepoPhase;
import dev.jagt.orchestrator.flow.repo.RequestPhase;
import dev.jagt.orchestrator.flow.session.AskStamp;
import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.StopKind;
import dev.jagt.orchestrator.flow.session.TurnKind;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.statechart.DataId;
import dev.jagt.statechart.Guard;
import dev.jagt.statechart.RegionId;
import dev.jagt.statechart.StatechartBuilder;
import dev.jagt.statechart.TransitionBuilder;
import java.util.function.Function;
import java.util.function.Predicate;

/** The regions of the task chart, its datum and the edge every region declares the same way. */
final class ChartRegions {

  private ChartRegions() {}

  static final RegionId<TaskStatus> LIFECYCLE = new RegionId<>("lifecycle", TaskStatus.class);

  static final RegionId<StopKind> STOP = new RegionId<>("stop", StopKind.class);

  static final RegionId<PlanGate> PLAN = new RegionId<>("plan", PlanGate.class);

  static final RegionId<AgentReport> REPORT = new RegionId<>("report", AgentReport.class);

  static final RegionId<TurnKind> TURN = new RegionId<>("turn", TurnKind.class);

  static final RegionId<DeployPhase> DEPLOY = new RegionId<>("deploy", DeployPhase.class);

  static final RegionId<SessionPhase> SESSION = new RegionId<>("session", SessionPhase.class);

  static final RegionId<GitPhase> GIT = new RegionId<>("git", GitPhase.class);

  static final RegionId<RepoPhase> REPO = new RegionId<>("repo", RepoPhase.class);

  static final RegionId<RequestPhase> REQUEST = new RegionId<>("request", RequestPhase.class);

  static final RegionId<ChecksPhase> CHECKS = new RegionId<>("checks", ChecksPhase.class);

  static final RegionId<ApprovalPhase> APPROVAL = new RegionId<>("approval", ApprovalPhase.class);

  static final RegionId<InfraPhase> INFRA = new RegionId<>("infra", InfraPhase.class);

  static final DataId<AskStamp> ASK = new DataId<>("ask", AskStamp.class);

  static final Guard<TaskMove> ALWAYS = (active, move) -> true;

  static <S extends Enum<S>> void regionEdge(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart,
      RegionId<S> region,
      S from,
      String label,
      Predicate<TaskMove> matches,
      Function<TaskMove, TaskEffect> effect,
      S to) {
    TransitionBuilder<TaskMove, ChartFacts, TaskEffect, S> edge =
        chart.onMatching(region, from, label, matches).emitFor(effect);
    if (from == to) {
      edge.internal();
    } else {
      edge.to(to);
    }
  }

  static boolean beginsRound(TaskMove move) {
    return move instanceof TaskMove.RequestLinked linked && (linked.newRound() || linked.relinks());
  }
}
