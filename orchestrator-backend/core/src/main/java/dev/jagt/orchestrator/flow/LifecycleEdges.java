package dev.jagt.orchestrator.flow;

import static dev.jagt.orchestrator.flow.ChartRegions.LIFECYCLE;
import static dev.jagt.orchestrator.flow.ChartRegions.PLAN;
import static dev.jagt.orchestrator.flow.StatusSets.BEFORE_THE_CODE;
import static dev.jagt.orchestrator.flow.StatusSets.BEFORE_THE_VERDICT;
import static dev.jagt.orchestrator.flow.StatusSets.HELD;
import static dev.jagt.orchestrator.flow.StatusSets.OPEN;
import static dev.jagt.orchestrator.flow.StatusSets.READ;
import static dev.jagt.orchestrator.flow.StatusSets.READ_FROM;
import static dev.jagt.orchestrator.flow.StatusSets.REPORTED;
import static dev.jagt.orchestrator.task.TaskStatus.CI_POLLING;
import static dev.jagt.orchestrator.task.TaskStatus.DEPLOYED;
import static dev.jagt.orchestrator.task.TaskStatus.DEPLOY_CONFLICT;
import static dev.jagt.orchestrator.task.TaskStatus.DONE;
import static dev.jagt.orchestrator.task.TaskStatus.IN_PROGRESS;
import static dev.jagt.orchestrator.task.TaskStatus.PLAN_PENDING;
import static dev.jagt.orchestrator.task.TaskStatus.REVERTED;
import static dev.jagt.orchestrator.task.TaskStatus.REVIEWED;
import static dev.jagt.orchestrator.task.TaskStatus.REVIEW_PENDING;
import static dev.jagt.orchestrator.task.TaskStatus.SHIPPING;
import static dev.jagt.orchestrator.task.TaskStatus.VERIFYING;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.statechart.Guard;
import dev.jagt.statechart.StatechartBuilder;
import dev.jagt.statechart.TransitionBuilder;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The lifecycle region's edges: what each outcome, report, read and verification does to a status.
 */
final class LifecycleEdges {

  private static final Set<TaskStatus> PAST_THE_PLAN =
      EnumSet.of(SHIPPING, REVIEW_PENDING, CI_POLLING);

  private static final Guard<TaskMove> PLAN_IS_OPEN =
      (active, move) -> active.state(PLAN) == PlanGate.OPEN;

  private LifecycleEdges() {}

  static void reads(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    for (TaskStatus concluded : READ) {
      edges(
          chart,
          READ_FROM,
          "read " + concluded,
          TaskMove.Read.class,
          new TaskMove.Read(concluded)::equals,
          concluded);
    }
  }

  static void ran(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    ran(chart, TaskAction.SHIP, Outcome.Kind.OK, CI_POLLING, TaskOffers.SHIP_FROM);
    ran(chart, TaskAction.SHIP, Outcome.Kind.RELAYED, SHIPPING, TaskOffers.SHIP_FROM);
    ran(
        chart,
        TaskAction.APPROVE_PLAN,
        Outcome.Kind.RELAYED,
        IN_PROGRESS,
        TaskOffers.APPROVE_PLAN_FROM);
    ran(
        chart,
        TaskAction.ADD_REPO,
        Outcome.Kind.RELAYED,
        IN_PROGRESS,
        EnumSet.complementOf(EnumSet.of(PLAN_PENDING, DEPLOYED, DEPLOY_CONFLICT, REVERTED, DONE)));
    TaskOffers.REMOVE_REPO_FROM.forEach(status ->
        edge(
            chart,
            status,
            "remove-repo RELAYED",
            TaskMove.Ran.class,
            new TaskMove.Ran(TaskAction.REMOVE_REPO, Outcome.Kind.RELAYED)::equals,
            status));
    TaskOffers.HAND_OFF_FROM.forEach(status ->
        edge(
            chart,
            status,
            "hand-off RELAYED",
            TaskMove.Ran.class,
            new TaskMove.Ran(TaskAction.HAND_OFF, Outcome.Kind.RELAYED)::equals,
            status));
    EnumSet.of(DEPLOYED, DEPLOY_CONFLICT)
        .forEach(status ->
            edge(
                chart,
                status,
                "revert RELAYED",
                TaskMove.Ran.class,
                new TaskMove.Ran(TaskAction.REVERT, Outcome.Kind.RELAYED)::equals,
                status));
    ran(chart, TaskAction.DEPLOY, Outcome.Kind.OK, DEPLOYED, TaskOffers.DEPLOY_FROM);
    ran(chart, TaskAction.DEPLOY, Outcome.Kind.CONFLICT, DEPLOY_CONFLICT, TaskOffers.DEPLOY_FROM);
    ran(chart, TaskAction.REVERT, Outcome.Kind.OK, REVERTED, OPEN);
    ran(chart, TaskAction.REVERT, Outcome.Kind.CONFLICT, DEPLOY_CONFLICT, TaskOffers.DEPLOY_FROM);
    ran(
        chart,
        TaskAction.REVERT,
        Outcome.Kind.PARTIAL,
        DEPLOYED,
        EnumSet.complementOf(EnumSet.of(DEPLOY_CONFLICT, DONE)));
    ran(chart, TaskAction.DONE, Outcome.Kind.GONE, DONE, OPEN);
  }

  private static void ran(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart,
      TaskAction action,
      Outcome.Kind outcome,
      TaskStatus to,
      Set<TaskStatus> from) {
    edges(
        chart,
        from,
        action.id() + " " + outcome,
        TaskMove.Ran.class,
        new TaskMove.Ran(action, outcome)::equals,
        to);
  }

  static void reports(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    for (TaskStatus to : REPORTED) {
      Set<TaskStatus> free = EnumSet.noneOf(TaskStatus.class);
      OPEN.stream().filter(from -> mayReport(from, to) && !HELD.contains(from)).forEach(free::add);
      Guard<TaskMove> open = PAST_THE_PLAN.contains(to) ? PLAN_IS_OPEN : (active, move) -> true;
      if (to == REVIEW_PENDING) {
        edges(
            chart,
            free,
            "report " + to + " owing verification",
            TaskMove.Reported.class,
            reportOf(to, true),
            open,
            VERIFYING);
      }
      edges(chart, free, "report " + to, TaskMove.Reported.class, reportOf(to, false), open, to);
      HELD.stream()
          .filter(held -> mayReport(held, to))
          .forEach(held ->
              edge(
                  chart, held, "report " + to, TaskMove.Reported.class, reportOf(to, false), held));
    }
  }

  private static Predicate<TaskMove> reportOf(TaskStatus to, boolean owedOnly) {
    return move ->
        move instanceof TaskMove.Reported report
            && report.status() == to
            && (!owedOnly || report.verificationOwed());
  }

  private static boolean mayReport(TaskStatus from, TaskStatus to) {
    return (to != PLAN_PENDING || BEFORE_THE_CODE.contains(from))
        && (to != CI_POLLING || BEFORE_THE_VERDICT.contains(from));
  }

  private static void edges(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart,
      Set<TaskStatus> from,
      String label,
      Class<? extends TaskMove> kind,
      Predicate<TaskMove> matches,
      TaskStatus to) {
    edges(chart, from, label, kind, matches, (active, move) -> true, to);
  }

  private static void edges(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart,
      Set<TaskStatus> from,
      String label,
      Class<? extends TaskMove> kind,
      Predicate<TaskMove> matches,
      Guard<TaskMove> when,
      TaskStatus to) {
    if (!from.isEmpty()) {
      chart
          .onMatching(LIFECYCLE, from, kind, label, matches)
          .when(when)
          .emit(new TaskEffect.Lands(to))
          .to(to);
    }
  }

  private static void edge(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart,
      TaskStatus from,
      String label,
      Class<? extends TaskMove> kind,
      Predicate<TaskMove> matches,
      TaskStatus to) {
    TransitionBuilder<TaskMove, ChartFacts, TaskEffect, TaskStatus> edge =
        chart.onMatching(LIFECYCLE, from, kind, label, matches).emit(new TaskEffect.Lands(to));
    if (from == to) {
      edge.internal();
    } else {
      edge.to(to);
    }
  }

  static void conflicts(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    edge(
        chart,
        DEPLOY_CONFLICT,
        "conflicted repository left",
        TaskMove.RepoLeft.class,
        move -> move instanceof TaskMove.RepoLeft left && left.landsTheTask(),
        DEPLOYED);
    edge(
        chart,
        DEPLOY_CONFLICT,
        "conflicted repository left, nothing landed",
        TaskMove.RepoLeft.class,
        move -> move instanceof TaskMove.RepoLeft left && left.returnsTheTask(),
        REVIEWED);
    edge(
        chart,
        DEPLOYED,
        "deploy read red",
        TaskMove.DeployRead.class,
        new TaskMove.DeployRead(true)::equals,
        DEPLOY_CONFLICT);
    edge(
        chart,
        DEPLOY_CONFLICT,
        "deploy read green",
        TaskMove.DeployRead.class,
        new TaskMove.DeployRead(false)::equals,
        DEPLOYED);
  }

  static void verification(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    edge(
        chart,
        VERIFYING,
        "verified",
        TaskMove.Verified.class,
        new TaskMove.Verified(true)::equals,
        REVIEW_PENDING);
    edge(
        chart,
        VERIFYING,
        "verification failed",
        TaskMove.Verified.class,
        new TaskMove.Verified(false)::equals,
        IN_PROGRESS);
  }
}
