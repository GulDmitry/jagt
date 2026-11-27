package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.deploy.Pipeline;
import dev.jagt.orchestrator.flow.deploy.RedCause;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** What a task's status allows and where an outcome leads it, answered by the task chart alone. */
public final class FlowRules {

  private final TaskChart chart;

  public FlowRules(TaskChart chart) {
    this.chart = chart;
  }

  public boolean allows(TaskStatus status, TaskAction action, Facts facts) {
    return chart.offers(status, action, facts);
  }

  /** As above, for a press by {@code origin} on facts that read git and the stops. */
  public boolean allows(
      TaskStatus status, TaskAction action, Facts facts, @Nullable ActionOrigin origin) {
    return chart.offers(status, action, facts, origin);
  }

  /**
   * Why a press the chart does not offer is declined by what the press itself reads; empty where
   * none of those says no.
   */
  public Optional<Decline> declined(TaskAction action, Facts facts, @Nullable ActionOrigin origin) {
    return chart.declined(action, facts, origin);
  }

  /**
   * Every action legal for a task, what moves it on before what only looks at it — so no surface
   * sorts.
   */
  public List<TaskAction> allowed(TaskStatus status, Facts facts) {
    return Arrays.stream(TaskAction.values())
        .filter(action -> allows(status, action, facts))
        .sorted(Comparator.comparing(TaskAction::group))
        .toList();
  }

  /**
   * The status this outcome leads a task in {@code from} to, or empty to leave the task where it
   * is.
   */
  public Optional<TaskStatus> next(TaskStatus from, TaskAction action, Outcome.Kind outcome) {
    return outcome == Outcome.Kind.GONE
        ? Optional.empty()
        : chart.land(from, new TaskMove.Ran(action, outcome));
  }

  /**
   * Whether the table says anything at all about this action — a verb it does not mention can never
   * run.
   */
  public static boolean mentions(TaskAction action) {
    return TaskOffers.mentions(action);
  }

  /** Every status any action can lead to. */
  public Set<TaskStatus> targets() {
    return chart.ranTargets();
  }

  /**
   * Whether a plan can still come first for a task in {@code status}: it has written nothing from
   * the code yet.
   */
  public boolean planMayComeFirst(TaskStatus status) {
    return chart.planMayComeFirst(status);
  }

  /** Whether a task may be moved to {@code status} by its own agent rather than by jagt. */
  public static boolean reportable(TaskStatus status) {
    return StatusSets.reportable(status);
  }

  /** The same question with the status the task is IN. */
  public boolean reportable(TaskStatus from, TaskStatus to) {
    return refusedReport(from, to).isEmpty();
  }

  /**
   * Why a task in {@code from} may not report {@code to}, or empty when it may. Two reports are not
   * source-agnostic: CI_POLLING says "a request is open and waiting", which for a task the review
   * has already passed drags it backwards and re-arms the unattended poll; REVERTED is a record no
   * agent may erase.
   */
  public Optional<String> refusedReport(TaskStatus from, TaskStatus to) {
    if (!reportable(to)) {
      return Optional.of(to + " is jagt's to set, not a task's to report");
    }
    if (chart.land(from, new TaskMove.Reported(to, false)).isPresent()) {
      return Optional.empty();
    }
    if (to == TaskStatus.PLAN_PENDING) {
      return Optional.of(
          to
              + " cannot be reported by a task that is already "
              + from
              + " — a plan is what comes before the code, not after it"
              + (from == TaskStatus.VERIFYING
                  ? "; report IN_PROGRESS first, then PLAN_PENDING with the new probes"
                  : ""));
    }
    return Optional.of(
        to
            + " cannot be reported by a task that is already "
            + from
            + " — that would take it backwards and start polling finished work");
  }

  /**
   * As {@link #refusedReport(TaskStatus, TaskStatus)}, for what the report means (a question ends
   * the round) and the plan gate: a hand-back or a ship is the code done, which a closed gate has
   * not seen.
   */
  public Optional<String> refusedReport(
      TaskStatus from, TaskStatus to, AgentReport means, PlanGate plan) {
    if (plan.closed()
        && refusedReport(from, to).isEmpty()
        && chart.land(from, plan, new TaskMove.Reported(to, false)).isEmpty()) {
      return Optional.of(
          to
              + " cannot be reported while the plan is not approved — report PLAN_PENDING with"
              + " the plan, or IN_PROGRESS with outcome=no_plan");
    }
    if (from == TaskStatus.CI_POLLING && to == TaskStatus.CI_POLLING && means.asks()) {
      return Optional.of(
          "a question cannot be reported while the task stands at CI_POLLING"
              + " — report REVIEW_PENDING with your question");
    }
    return refusedReport(from, to);
  }

  /**
   * The status a report actually lands on. A task at REVERTED KEEPS it: what came back out of a
   * shared branch is a human's to move on from; one at DEPLOY_CONFLICT keeps it until the deploy
   * finishes. The report is ACCEPTED rather than refused — an agent's protocol is to keep saying
   * what it is doing, and a status it cannot report is a session whose every call errors. Such a
   * status keeps its line too ({@link #holdsItsLine}).
   */
  public TaskStatus reported(TaskStatus from, TaskStatus to) {
    return reported(from, to, false);
  }

  /**
   * The same, told whether this project still owes a verification run. A hand-back waits at
   * VERIFYING until jagt has run the project's own command, so a human never opens a red tree.
   */
  public TaskStatus reported(TaskStatus from, TaskStatus to, boolean verificationOwed) {
    return chart.land(from, new TaskMove.Reported(to, verificationOwed)).orElse(to);
  }

  /**
   * Whether a report leaves the task's line alone, a question aside: the status's own fact is the
   * human's.
   */
  public static boolean holdsItsLine(TaskStatus status) {
    return StatusSets.holds(status);
  }

  /**
   * Whether the task's next step waits on the host's checks, so a red or green read can move it.
   */
  public static boolean waitsOnTheHost(TaskStatus status) {
    return StatusSets.waitsOnTheHost(status);
  }

  /** Whether the task is closed for good. */
  public static boolean finished(TaskStatus status) {
    return StatusSets.finished(status);
  }

  /** Whether the agent has handed the change over and the review is the next step. */
  public static boolean awaitsReview(TaskStatus status) {
    return StatusSets.awaitsReview(status);
  }

  /** Whether the agent is working on the change. */
  public static boolean inProgress(TaskStatus status) {
    return StatusSets.inProgress(status);
  }

  /** Whether the agent is shipping the change. */
  public static boolean shipping(TaskStatus status) {
    return StatusSets.shipping(status);
  }

  /**
   * Whether a read of the review round leaves this report standing: only the session or a human
   * answers it.
   */
  public static boolean standsThroughRead(AgentReport report) {
    return StatusSets.standsThroughRead(report);
  }

  /** What a read of the review round can conclude, which only the host's round read may land. */
  public static Set<TaskStatus> reads() {
    return StatusSets.reads();
  }

  /**
   * Where a round read lands: only a task waiting on the host moves; empty where the chart ignores
   * the read, a session on its status keeping it.
   */
  public Optional<TaskStatus> readLands(TaskStatus from, TaskStatus read) {
    if (!StatusSets.reads().contains(read)) {
      throw new IllegalArgumentException(
          read + " is not what a read of the review round concludes");
    }
    return chart.land(from, new TaskMove.Read(read));
  }

  /**
   * What a read of the review round leads to: a red run stops it unless it died on infrastructure
   * with a retry left, a clean round is approved or reviewed, and anything else leaves the task
   * where its session has it.
   */
  public static Optional<TaskStatus> readReview(
      boolean unresolved, boolean approved, Pipeline checks, RedCause cause, boolean retryLeft) {
    return TaskChart.readConcludes(unresolved, approved, checks, cause, retryLeft);
  }

  /** A verified hand-back reaches the human; a failed one goes back to its session. */
  public TaskStatus verified(boolean passed) {
    return chart.land(TaskStatus.VERIFYING, new TaskMove.Verified(passed)).orElseThrow();
  }

  /** A session handed findings, an answer or a go is at work again. */
  public static TaskStatus relayed() {
    return TaskStatus.IN_PROGRESS;
  }

  /** A task resumed onto a request already open waits on that request's checks. */
  public static TaskStatus resumedOnRequest() {
    return TaskStatus.CI_POLLING;
  }

  /**
   * A stop of this trigger is a loop jagt suspected, which in act the Master decides; the others
   * are the human's or end on a read.
   */
  public static boolean liftedByTheArbiter(TaskStop.Trigger trigger) {
    return LIFTED_BY_THE_ARBITER.contains(trigger);
  }

  /** A closed task: its worktree is gone, so nothing can be relayed into it. */
  public static boolean closed(TaskStatus status) {
    return status == TaskStatus.DONE;
  }

  /**
   * Where the agent is expected to act, so its silence means death; NEW for one that died before
   * reporting.
   */
  public static boolean watched(TaskStatus status) {
    return StatusSets.watched(status);
  }

  /**
   * A status a session hands control back with: news to the human, and where its drafted replies
   * wait.
   */
  public static boolean handsBack(TaskStatus status) {
    return StatusSets.handedBack(status);
  }

  /** A plan or a round handed back, which the Master reads before the human where it runs. */
  public static boolean readByTheArbiter(TaskStatus status) {
    return StatusSets.readByTheArbiter(status);
  }

  /** A hand-back the Master reads next, once verification has passed where one runs. */
  public static boolean headedForTheArbiter(TaskStatus status) {
    return awaitingVerification(status) || readByTheArbiter(status);
  }

  /** Read and green, with only the approval or the deploy still to come. */
  public static boolean awaitsApproval(TaskStatus status) {
    return StatusSets.waitsForDeploy(status);
  }

  /**
   * A request green with every thread closed, which in act the Master deploys as the human would.
   */
  public static boolean deployedByTheArbiter(TaskStatus status) {
    return StatusSets.waitsForDeploy(status);
  }

  /** As above, where {@code requireApproval} leaves a request read but not yet approved alone. */
  public static boolean deployedByTheArbiter(TaskStatus status, boolean requireApproval) {
    return deployedByTheArbiter(status) && !(requireApproval && FlowRules.reviewed(status));
  }

  /**
   * A round is out with the reviewers, so only the code host moves it on. NOT what the unattended
   * poll watches, which is an open request whatever the status.
   */
  public static boolean outForReview(TaskStatus status) {
    return StatusSets.outForReview(status);
  }

  /** Green with nothing left for its session, so only a reviewer's word can move the request on. */
  public static boolean movedOnByReviewerAlone(TaskStatus status) {
    return StatusSets.handedOver(status);
  }

  /**
   * A hand-back jagt holds until it has run the project's verification command; its agent's silence
   * is no stall.
   */
  public static boolean awaitingVerification(TaskStatus status) {
    return status == TaskStatus.VERIFYING;
  }

  /** Live on the deploy branch, so its session checks the change where it landed. */
  public static boolean checkedWhereItLanded(TaskStatus status) {
    return status == TaskStatus.DEPLOYED;
  }

  /** Handed back as a deploy conflict, which a pipeline read on the deploy branch may still end. */
  public static boolean deployConflicted(TaskStatus status) {
    return status == TaskStatus.DEPLOY_CONFLICT;
  }

  /**
   * Where a read of the pipeline on a deploy commit lands: only a task live on the deploy branch
   * moves, and only on a red the code owns; empty where the chart ignores the read.
   */
  public Optional<TaskStatus> deployReadLands(TaskStatus from, boolean redOnTheCode) {
    return chart.land(from, new TaskMove.DeployRead(redOnTheCode));
  }

  /** A plan waiting to be read, rather than a round of code. */
  public static boolean holdsPlan(TaskStatus status) {
    return status == TaskStatus.PLAN_PENDING;
  }

  /** A deploy waiting in the deploy worktree, where its conflict is resolved. */
  public static boolean conflictedInTheDeployWorktree(TaskStatus status) {
    return status == TaskStatus.DEPLOY_CONFLICT;
  }

  /**
   * Waiting on an instruction jagt relayed, which nothing else written to the session may replace.
   */
  public static boolean awaitsItsInstruction(TaskStatus status) {
    return StatusSets.awaitsItsInstruction(status);
  }

  /** A round read that found the request approved. */
  public static boolean approved(TaskStatus read) {
    return read == TaskStatus.APPROVED;
  }

  /** A round read that found nothing unresolved and the checks green, with no approval yet. */
  public static boolean reviewed(TaskStatus read) {
    return read == TaskStatus.REVIEWED;
  }

  /**
   * Whether the work has left the worktree, which is the only point a tracker's word can end the
   * task.
   */
  public static boolean handedOver(TaskStatus status) {
    return StatusSets.handedOver(status);
  }

  /**
   * Statuses no action leads to and no task reports: a redirect in {@link #reported} is the only
   * way in.
   */
  public Set<TaskStatus> redirects() {
    return chart.verifiable();
  }

  private static final Set<TaskStop.Trigger> LIFTED_BY_THE_ARBITER =
      EnumSet.of(
          TaskStop.Trigger.STALLED,
          TaskStop.Trigger.SHIPS,
          TaskStop.Trigger.ROUNDS,
          TaskStop.Trigger.PACE,
          TaskStop.Trigger.UNCHANGED_FAILURE);
}
