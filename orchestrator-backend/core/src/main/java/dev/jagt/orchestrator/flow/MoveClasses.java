package dev.jagt.orchestrator.flow;

import static dev.jagt.orchestrator.flow.ChartRegions.APPROVAL;
import static dev.jagt.orchestrator.flow.ChartRegions.CHECKS;
import static dev.jagt.orchestrator.flow.ChartRegions.DEPLOY;
import static dev.jagt.orchestrator.flow.ChartRegions.GIT;
import static dev.jagt.orchestrator.flow.ChartRegions.INFRA;
import static dev.jagt.orchestrator.flow.ChartRegions.LIFECYCLE;
import static dev.jagt.orchestrator.flow.ChartRegions.PLAN;
import static dev.jagt.orchestrator.flow.ChartRegions.REPO;
import static dev.jagt.orchestrator.flow.ChartRegions.REPORT;
import static dev.jagt.orchestrator.flow.ChartRegions.REQUEST;
import static dev.jagt.orchestrator.flow.ChartRegions.SESSION;
import static dev.jagt.orchestrator.flow.ChartRegions.STOP;
import static dev.jagt.orchestrator.flow.ChartRegions.TURN;

import dev.jagt.orchestrator.flow.deploy.DeployPhase;
import dev.jagt.orchestrator.flow.deploy.InfraPhase;
import dev.jagt.orchestrator.flow.repo.GitPhase;
import dev.jagt.orchestrator.flow.repo.RepoPhase;
import dev.jagt.orchestrator.flow.repo.RequestPhase;
import dev.jagt.orchestrator.flow.session.StopKind;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.statechart.RegionId;
import dev.jagt.statechart.StatechartBuilder;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which regions each kind of move is the concern of, and what it does in every state of them that
 * no edge takes it in.
 */
final class MoveClasses {

  private MoveClasses() {}

  static void declare(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.moves(TaskMove.class);
    chart.addressed(TaskMove.ApprovalAsked.class, PLAN);
    chart.addressed(TaskMove.ApprovalRead.class, APPROVAL);
    chart.addressed(TaskMove.Autoplanned.class, PLAN);
    chart.addressed(TaskMove.ChecksRead.class, CHECKS);
    chart.addressed(TaskMove.ChecksUnread.class, CHECKS);
    chart.addressed(TaskMove.DeployRead.class, LIFECYCLE, REPORT);
    chart.addressed(TaskMove.DeployStepped.class, DEPLOY);
    chart.addressed(TaskMove.Escalated.class, STOP);
    chart.addressed(TaskMove.GitChanged.class, GIT);
    chart.addressed(TaskMove.GitRepoJoined.class, GIT);
    chart.addressed(TaskMove.GitTrusted.class, GIT);
    chart.addressed(TaskMove.HeadSeen.class, CHECKS, INFRA);
    chart.addressed(TaskMove.InfraArmed.class, INFRA);
    chart.addressed(TaskMove.InfraCleared.class, INFRA);
    chart.addressed(TaskMove.InfraSent.class, INFRA);
    chart.addressed(TaskMove.Lifted.class, STOP);
    chart.addressed(TaskMove.MergedByGit.class, REQUEST);
    chart.addressed(TaskMove.MergedByHost.class, REQUEST);
    chart.addressed(TaskMove.NoPlanDeclared.class, PLAN);
    chart.addressed(TaskMove.Offers.class, LIFECYCLE);
    chart.addressed(TaskMove.Placed.class, REPO);
    chart.addressed(TaskMove.PlanApproved.class, PLAN);
    chart.addressed(TaskMove.PlanAsked.class, PLAN);
    chart.addressed(TaskMove.Ran.class, LIFECYCLE);
    chart.addressed(TaskMove.Read.class, LIFECYCLE, REPORT);
    chart.addressed(TaskMove.Relayed.class, PLAN, TURN);
    chart.addressed(TaskMove.RepoAsked.class, REPO);
    chart.addressed(TaskMove.RepoAttached.class, REPO, REQUEST);
    chart.addressed(TaskMove.RepoLeft.class, DEPLOY, LIFECYCLE, REQUEST);
    chart.addressed(TaskMove.RepoUnasked.class, REPO);
    chart.addressed(TaskMove.Reported.class, LIFECYCLE);
    chart.addressed(TaskMove.RequestLinked.class, APPROVAL, CHECKS, INFRA, REQUEST);
    chart.addressed(TaskMove.RequestReplaced.class, STOP);
    chart.addressed(TaskMove.Said.class, REPORT);
    chart.addressed(TaskMove.SessionStartedOver.class, SESSION);
    chart.addressed(TaskMove.Signed.class, SESSION);
    chart.addressed(TaskMove.Stamped.class, REPORT);
    chart.addressed(TaskMove.StopEnded.class, STOP);
    chart.addressed(TaskMove.StopSet.class, STOP);
    chart.addressed(TaskMove.TurnClosed.class, TURN);
    chart.addressed(TaskMove.TurnRestored.class, TURN);
    chart.addressed(TaskMove.Verified.class, LIFECYCLE, REPORT);
    lifecycle(chart);
    progress(chart);
    delivery(chart);
  }

  private static void lifecycle(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    ignored(
        chart,
        TaskMove.DeployRead.class,
        LIFECYCLE,
        "only a task live on the deploy branch, or handed back from it, moves on a pipeline read",
        EnumSet.of(
            TaskStatus.APPROVED,
            TaskStatus.CI_FAILED,
            TaskStatus.CI_POLLING,
            TaskStatus.DONE,
            TaskStatus.IN_PROGRESS,
            TaskStatus.NEW,
            TaskStatus.PLAN_PENDING,
            TaskStatus.REVERTED,
            TaskStatus.REVIEW_PENDING,
            TaskStatus.REVIEWED,
            TaskStatus.SHIPPING,
            TaskStatus.VERIFYING));
    ignored(
        chart,
        TaskMove.Read.class,
        LIFECYCLE,
        "a round read moves only a task waiting on the host or red; other work stays",
        EnumSet.of(
            TaskStatus.DEPLOY_CONFLICT,
            TaskStatus.DEPLOYED,
            TaskStatus.DONE,
            TaskStatus.IN_PROGRESS,
            TaskStatus.NEW,
            TaskStatus.PLAN_PENDING,
            TaskStatus.REVERTED,
            TaskStatus.REVIEW_PENDING,
            TaskStatus.SHIPPING,
            TaskStatus.VERIFYING));
    ignored(
        chart,
        TaskMove.RepoLeft.class,
        LIFECYCLE,
        "a repository leaving moves the status only where the deploy stands in conflict on it",
        EnumSet.of(
            TaskStatus.APPROVED,
            TaskStatus.CI_FAILED,
            TaskStatus.CI_POLLING,
            TaskStatus.DEPLOYED,
            TaskStatus.DONE,
            TaskStatus.IN_PROGRESS,
            TaskStatus.NEW,
            TaskStatus.PLAN_PENDING,
            TaskStatus.REVERTED,
            TaskStatus.REVIEW_PENDING,
            TaskStatus.REVIEWED,
            TaskStatus.SHIPPING,
            TaskStatus.VERIFYING));
    ignored(
        chart,
        TaskMove.Verified.class,
        LIFECYCLE,
        "only a hand-back waiting at verification takes the verification result",
        EnumSet.of(
            TaskStatus.APPROVED,
            TaskStatus.CI_FAILED,
            TaskStatus.CI_POLLING,
            TaskStatus.DEPLOY_CONFLICT,
            TaskStatus.DEPLOYED,
            TaskStatus.DONE,
            TaskStatus.IN_PROGRESS,
            TaskStatus.NEW,
            TaskStatus.PLAN_PENDING,
            TaskStatus.REVERTED,
            TaskStatus.REVIEW_PENDING,
            TaskStatus.REVIEWED,
            TaskStatus.SHIPPING));
    refused(
        chart,
        TaskMove.Ran.class,
        LIFECYCLE,
        "a closed task presses nothing",
        EnumSet.of(TaskStatus.DONE));
    refused(
        chart,
        TaskMove.Reported.class,
        LIFECYCLE,
        "a closed task is reported to",
        EnumSet.of(TaskStatus.DONE));
  }

  private static void progress(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    ignored(
        chart,
        TaskMove.Escalated.class,
        STOP,
        "only a stop that ends on a lift is escalated to the next model tier",
        EnumSet.of(
            StopKind.NONE,
            StopKind.HOST_READ,
            StopKind.SESSION_ALIVE,
            StopKind.SESSION_REPORT,
            StopKind.DEPLOY_READ,
            StopKind.PAUSE));
    ignored(
        chart,
        TaskMove.StopSet.class,
        STOP,
        "where a stop already stands, it stands alone",
        EnumSet.of(
            StopKind.LIFT,
            StopKind.HOST_READ,
            StopKind.SESSION_ALIVE,
            StopKind.SESSION_REPORT,
            StopKind.DEPLOY_READ,
            StopKind.PAUSE));
    ignored(
        chart,
        TaskMove.RequestReplaced.class,
        STOP,
        "a new request voids only a stop counted on a request; a pause is the human's",
        EnumSet.of(StopKind.NONE, StopKind.PAUSE));
    ignored(
        chart,
        TaskMove.StopEnded.class,
        STOP,
        "a read ends only a stop that ends on one; a pause ends by a lift",
        EnumSet.of(StopKind.NONE, StopKind.PAUSE));
    ignored(
        chart,
        TaskMove.Autoplanned.class,
        PLAN,
        "a gate already shut, or one the human asked for, stays as it is",
        EnumSet.of(PlanGate.CLOSED, PlanGate.REQUIRED));
    ignored(
        chart,
        TaskMove.ApprovalAsked.class,
        PLAN,
        "an open gate has no plan to approve",
        EnumSet.of(PlanGate.OPEN));
    ignored(
        chart,
        TaskMove.NoPlanDeclared.class,
        PLAN,
        "a session declares needless only a gate jagt shut; one the human asked for stays",
        EnumSet.of(PlanGate.OPEN, PlanGate.REQUIRED));
    ignored(
        chart,
        TaskMove.PlanApproved.class,
        PLAN,
        "an open gate has no plan to approve",
        EnumSet.of(PlanGate.OPEN));
    ignored(
        chart,
        TaskMove.PlanAsked.class,
        PLAN,
        "the human already asked for a plan",
        EnumSet.of(PlanGate.REQUIRED));
    ignored(
        chart,
        TaskMove.Relayed.class,
        PLAN,
        "a round past the plan opens only a shut gate; a required one waits for approval",
        EnumSet.of(PlanGate.OPEN, PlanGate.REQUIRED));
    ignored(
        chart,
        TaskMove.Read.class,
        REPORT,
        "a round read leaves a question or permission standing; only an answer ends it",
        EnumSet.of(AgentReport.PERMISSION, AgentReport.QUESTION));
    ignored(
        chart,
        TaskMove.Read.class,
        REPORT,
        "no question or permission stands to clear",
        EnumSet.of(AgentReport.PLAIN));
    ignored(
        chart,
        TaskMove.DeployRead.class,
        REPORT,
        "no question or permission stands to clear",
        EnumSet.of(AgentReport.PLAIN));
    ignored(
        chart,
        TaskMove.Verified.class,
        REPORT,
        "no question or permission stands to clear",
        EnumSet.of(AgentReport.PLAIN));
  }

  private static void delivery(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    ignored(
        chart,
        TaskMove.RepoLeft.class,
        DEPLOY,
        "a repository leaving matters to the deploy only where it was handed back",
        EnumSet.of(
            DeployPhase.NONE, DeployPhase.LANDING, DeployPhase.REVERTING, DeployPhase.CHECK_ASKED));
    ignored(
        chart,
        TaskMove.GitChanged.class,
        GIT,
        "nothing was trusted yet, so nothing can have changed",
        EnumSet.of(GitPhase.UNREAD));
    ignored(
        chart,
        TaskMove.GitRepoJoined.class,
        GIT,
        "nothing was trusted yet for a repository to join",
        EnumSet.of(GitPhase.UNREAD));
    ignored(
        chart,
        TaskMove.HeadSeen.class,
        INFRA,
        "a new head forgets only an armed re-run",
        EnumSet.of(InfraPhase.NONE, InfraPhase.SENT, InfraPhase.SPENT));
    ignored(
        chart,
        TaskMove.RepoUnasked.class,
        REPO,
        "no repository was asked for",
        EnumSet.of(RepoPhase.NONE));
    ignored(
        chart,
        TaskMove.RepoAttached.class,
        REQUEST,
        "the round is already open",
        EnumSet.of(RequestPhase.OPEN));
  }

  private static <S extends Enum<S>> void ignored(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart,
      Class<? extends TaskMove> kind,
      RegionId<S> region,
      String why,
      Set<S> states) {
    chart.ignores(region, kind, states, why);
  }

  private static <S extends Enum<S>> void refused(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart,
      Class<? extends TaskMove> kind,
      RegionId<S> region,
      String named,
      Set<S> states) {
    chart.refuses(region, kind, states, named);
  }
}
