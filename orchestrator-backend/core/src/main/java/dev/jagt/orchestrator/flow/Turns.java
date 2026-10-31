package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn.Holder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Who holds a task's turn once its status, its last report and whether jagt stopped it stand, under the Master's mode
 * and the steps kept.
 */
public final class Turns {

    private Turns() {
    }

    /** A question rides on the plan or round the Master reads; elsewhere no model grants a right. A check a checker reads holds it. */
    public static Holder next(TaskStatus status, AgentReport report, boolean stopped,
                              Map<String, CheckerPhase> checkers, Arbiter.Profile profile) {
        if (FlowRules.closed(status)) {
            return Holder.NOBODY;
        }
        if (stopped) {
            return Holder.HUMAN;
        }
        Optional<String> reading = Checkers.reading(checkers);
        if (reading.isPresent()) {
            return new Holder.Role(reading.get());
        }
        if (FlowRules.readByTheMaster(status) && profile.running()) {
            return Holder.ARBITER;
        }
        if (report.asks()) {
            return asked(report, profile);
        }
        if (FlowRules.deployedByTheMaster(status) && profile.may(MasterRight.DEPLOY)) {
            return Holder.ARBITER;
        }
        if (status == TaskStatus.REVIEW_PENDING && report == AgentReport.NO_CHANGES) {
            return Holder.CI;
        }
        return of(status);
    }

    private static Holder asked(AgentReport report, Arbiter.Profile profile) {
        return report == AgentReport.QUESTION && profile.may(MasterRight.ANSWER) ? Holder.ARBITER : Holder.HUMAN;
    }

    private static Holder of(TaskStatus status) {
        return switch (status) {
            case NEW, IN_PROGRESS, VERIFYING, SHIPPING -> Holder.SESSION;
            case CI_POLLING, REVIEWED -> Holder.CI;
            case PLAN_PENDING, REVIEW_PENDING, CI_FAILED, APPROVED, DEPLOY_CONFLICT, REVERTED -> Holder.HUMAN;
            case DEPLOYED, DONE -> Holder.NOBODY;
        };
    }
}
