package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn.Holder;

import java.util.EnumSet;
import java.util.Set;

/**
 * Who holds a task's turn once its status, its last report and whether jagt stopped it stand, under the Master's mode
 * and the steps kept.
 */
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

    /** Who holds {@code task}'s turn, the Master standing in only where it did not leave the task to the human. */
    public static Holder owner(TaskState task, Profile configured) {
        Profile profile = MasterPhase.of(task) == MasterPhase.LEFT ? Profile.OFF : configured;
        return next(task.status(), task.report(), task.stopped(), QaPhase.of(task), profile);
    }

    /** A question rides on the plan or round the Master reads; elsewhere no model grants a right. A check QA reads holds it. */
    public static Holder next(TaskStatus status, AgentReport report, boolean stopped, QaPhase qa,
                              Profile profile) {
        if (FlowRules.closed(status)) {
            return Holder.NOBODY;
        }
        if (stopped) {
            return Holder.HUMAN;
        }
        if (qa == QaPhase.READING) {
            return Holder.QA;
        }
        if (FlowRules.readByTheMaster(status) && profile.running()) {
            return Holder.MASTER;
        }
        if (report.asks()) {
            return asked(report, profile);
        }
        if (FlowRules.deployedByTheMaster(status) && profile.may(MasterRight.DEPLOY)) {
            return Holder.MASTER;
        }
        if (status == TaskStatus.REVIEW_PENDING && report == AgentReport.NO_CHANGES) {
            return Holder.CI;
        }
        return of(status);
    }

    private static Holder asked(AgentReport report, Profile profile) {
        return report == AgentReport.QUESTION && profile.may(MasterRight.ANSWER) ? Holder.MASTER : Holder.HUMAN;
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
