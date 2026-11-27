package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.flow.session.StopHold;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.MasterRight;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.Turn.Holder;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Who holds a task's turn once its status, its last report and whether jagt stopped it stand, under
 * the Master's mode and the steps kept.
 */
public final class Turns {

  private Turns() {}

  /**
   * A question rides on the plan or round the Master reads; elsewhere no model grants a right. A
   * check a checker reads holds it; the approval a request waits on and its checks are {@code
   * facts}.
   */
  public static Holder next(
      TaskStatus status,
      AgentReport report,
      StopHold stop,
      Map<String, CheckerPhase> checkers,
      Arbiter.Profile profile,
      Facts facts) {
    if (FlowRules.closed(status)) {
      return Holder.NOBODY;
    }
    if (stop != StopHold.NONE) {
      return stop == StopHold.ARBITER ? Holder.ARBITER : Holder.HUMAN;
    }
    Optional<String> reading = Checkers.reading(checkers);
    if (reading.isPresent()) {
      return new Holder.Role(reading.get());
    }
    if (FlowRules.readByTheArbiter(status) && profile.running()) {
      return Holder.ARBITER;
    }
    if (report.asks()) {
      return asked(report, profile);
    }
    Holder deploy = deploying(status, profile, facts);
    if (deploy != null) {
      return deploy;
    }
    if (status == TaskStatus.REVIEW_PENDING && report == AgentReport.NO_CHANGES) {
      return Holder.CI;
    }
    return of(status);
  }

  private static @Nullable Holder deploying(
      TaskStatus status, Arbiter.Profile profile, Facts facts) {
    if (facts.delivery().checksPending() && StatusSets.waitsForDeploy(status)) {
      return Holder.CI;
    }
    return FlowRules.deployedByTheArbiter(status, facts.reach().requireApproval())
            && profile.may(MasterRight.DEPLOY)
        ? Holder.ARBITER
        : null;
  }

  private static Holder asked(AgentReport report, Arbiter.Profile profile) {
    return report == AgentReport.QUESTION && profile.may(MasterRight.ANSWER)
        ? Holder.ARBITER
        : Holder.HUMAN;
  }

  private static Holder of(TaskStatus status) {
    return switch (status) {
      case NEW, IN_PROGRESS, VERIFYING, SHIPPING -> Holder.SESSION;
      case CI_POLLING, REVIEWED -> Holder.CI;
      case PLAN_PENDING, REVIEW_PENDING, CI_FAILED, APPROVED, DEPLOY_CONFLICT, REVERTED ->
          Holder.HUMAN;
      case DEPLOYED, DONE -> Holder.NOBODY;
    };
  }
}
