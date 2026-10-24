package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn.Holder;

import java.util.EnumSet;
import java.util.Set;

/** Who holds a task's turn once its status and its last report stand, under the Master's mode and the steps kept. */
public final class Turns {

    /** {@code mine} are the steps the human keeps even where the Master acts. */
    public record Profile(MasterMode mode, Set<MasterRight> mine) {

        public static final Profile OFF = new Profile(MasterMode.OFF, Set.of());

        public Profile {
            mine = mine.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(mine));
        }

        public boolean running() {
            return mode != MasterMode.OFF;
        }

        public boolean may(MasterRight right) {
            return mode == MasterMode.ACT && !mine.contains(right);
        }
    }

    private Turns() {
    }

    /** A question rides on the plan or round the Master reads; elsewhere no model grants a right. */
    public static Holder next(TaskStatus status, AgentReport report, Profile profile) {
        if (FlowRules.closed(status)) {
            return Holder.NOBODY;
        }
        if (FlowRules.readByTheMaster(status) && profile.running()) {
            return Holder.MASTER;
        }
        if (report.asks()) {
            return report == AgentReport.QUESTION && profile.may(MasterRight.ANSWER) ? Holder.MASTER : Holder.HUMAN;
        }
        if (FlowRules.deployedByTheMaster(status) && profile.may(MasterRight.DEPLOY)) {
            return Holder.MASTER;
        }
        if (status == TaskStatus.REVIEW_PENDING && report == AgentReport.NO_CHANGES) {
            return Holder.CI;
        }
        return of(status);
    }

    /** Whose turn a status alone means, where nobody stands in for the human. */
    public static Holder of(TaskStatus status) {
        return switch (status) {
            case NEW, IN_PROGRESS, VERIFYING, SHIPPING -> Holder.SESSION;
            case CI_POLLING, REVIEWED -> Holder.CI;
            case PLAN_PENDING, REVIEW_PENDING, CI_FAILED, APPROVED, DEPLOY_CONFLICT, REVERTED -> Holder.HUMAN;
            case DEPLOYED, DONE -> Holder.NOBODY;
        };
    }
}
