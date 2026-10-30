package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.statechart.Chart;
import dev.jagt.statechart.ChartBuilder;
import dev.jagt.statechart.Condition;
import dev.jagt.statechart.EdgeBuilder;
import dev.jagt.statechart.RegionId;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static dev.jagt.orchestrator.task.TaskStatus.*;

/** The lifecycle region, one edge per status move; the stop region, one way out per way a stop ends; the plan region; the report region; the turn region. */
final class TaskChart {

    private static final RegionId<TaskStatus> LIFECYCLE = new RegionId<>("lifecycle", TaskStatus.class);

    private static final RegionId<StopKind> STOP = new RegionId<>("stop", StopKind.class);

    private static final RegionId<PlanGate> PLAN = new RegionId<>("plan", PlanGate.class);

    private static final RegionId<AgentReport> REPORT = new RegionId<>("report", AgentReport.class);

    private static final RegionId<TurnKind> TURN = new RegionId<>("turn", TurnKind.class);

    private static final RegionId<DeployPhase> DEPLOY = new RegionId<>("deploy", DeployPhase.class);

    private static final Condition<TaskMove> ALWAYS = (others, move) -> true;

    private static final Object NO_FACTS = new Object();

    private static final Set<TaskStatus> OPEN = EnumSet.complementOf(EnumSet.of(DONE));

    private static final Set<TaskStatus> SHIP_FROM = EnumSet.of(IN_PROGRESS, VERIFYING, REVIEW_PENDING, SHIPPING,
            CI_POLLING, CI_FAILED, REVIEWED, DEPLOYED, REVERTED);

    private static final Set<TaskStatus> DEPLOY_FROM = EnumSet.of(REVIEW_PENDING, CI_POLLING, CI_FAILED, REVIEWED,
            APPROVED, DEPLOYED, DEPLOY_CONFLICT);

    private static final Set<TaskStatus> HELD = EnumSet.of(REVERTED, DEPLOY_CONFLICT);

    private static final Set<TaskStatus> REPORTED = EnumSet.of(PLAN_PENDING, IN_PROGRESS, SHIPPING, REVIEW_PENDING,
            CI_FAILED, CI_POLLING);

    private static final Set<TaskStatus> BEFORE_THE_CODE = EnumSet.of(NEW, PLAN_PENDING, IN_PROGRESS);

    private static final Set<TaskStatus> BEFORE_THE_VERDICT = EnumSet.of(NEW, IN_PROGRESS, SHIPPING, REVIEW_PENDING,
            CI_POLLING, CI_FAILED);

    private static final Set<TaskStatus> READ = EnumSet.of(REVIEWED, APPROVED, CI_FAILED);

    private static final Set<TaskStatus> READ_FROM = EnumSet.of(CI_POLLING, REVIEWED, APPROVED, CI_FAILED);

    private static final Set<AgentReport> STANDING_THROUGH_A_READ = EnumSet.of(AgentReport.QUESTION,
            AgentReport.PERMISSION);

    private static final Chart<TaskMove, Object, TaskEffect> CHART = build();

    private TaskChart() {
    }

    static Chart<TaskMove, Object, TaskEffect> chart() {
        return CHART;
    }

    /** What {@code move} does to a task at {@code from} under {@code stop}, {@code plan}, {@code report} and {@code turn}, in order; empty where no edge takes it. */
    static List<TaskEffect> effects(TaskStatus from, StopKind stop, PlanGate plan, AgentReport report,
                                TurnKind turn, DeployPhase deploy, TaskMove move) {
        return CHART.step(CHART.configuration(Map.of(LIFECYCLE.name(), from.name(), STOP.name(), stop.name(),
                PLAN.name(), plan.name(), REPORT.name(), report.name(), TURN.name(), turn.name(),
                DEPLOY.name(), deploy.name())), NO_FACTS, move).effects();
    }

    /** Where {@code move} leads a task at {@code from}, or empty where the chart has no edge for it. */
    static Optional<TaskStatus> land(TaskStatus from, TaskMove move) {
        return effects(from, StopKind.NONE, PlanGate.OPEN, AgentReport.PLAIN, TurnKind.CLOSED,
                DeployPhase.NONE, move).stream().filter(TaskEffect.Lands.class::isInstance)
                .map(effect -> ((TaskEffect.Lands) effect).to()).findFirst();
    }

    /** The statuses an action leads out of, whatever its outcome. */
    static Set<TaskStatus> leavesBy(TaskAction action) {
        Set<TaskStatus> leaves = EnumSet.noneOf(TaskStatus.class);
        for (TaskStatus from : TaskStatus.values()) {
            for (Outcome.Kind outcome : Outcome.Kind.values()) {
                land(from, new TaskMove.Ran(action, outcome)).ifPresent(to -> leaves.add(from));
            }
        }
        return leaves;
    }

    /** Every status some action's outcome leads to; a task that is gone is written nothing. */
    static Set<TaskStatus> ranTargets() {
        Set<TaskStatus> targets = EnumSet.noneOf(TaskStatus.class);
        for (TaskAction action : TaskAction.values()) {
            for (TaskStatus from : TaskStatus.values()) {
                for (Outcome.Kind outcome : Outcome.Kind.values()) {
                    if (outcome != Outcome.Kind.GONE) {
                        land(from, new TaskMove.Ran(action, outcome)).ifPresent(targets::add);
                    }
                }
            }
        }
        return targets;
    }

    static boolean reportable(TaskStatus status) {
        return REPORTED.contains(status);
    }

    static boolean holds(TaskStatus status) {
        return HELD.contains(status);
    }

    static boolean standsThroughARead(AgentReport report) {
        return STANDING_THROUGH_A_READ.contains(report);
    }

    static Set<TaskStatus> reads() {
        return EnumSet.copyOf(READ);
    }

    private static Chart<TaskMove, Object, TaskEffect> build() {
        ChartBuilder<TaskMove, Object, TaskEffect> chart = Chart.builder();
        chart.region(LIFECYCLE, NEW, Set.of(DONE));
        stops(chart);
        plans(chart);
        says(chart);
        turns(chart);
        deploys(chart);
        ran(chart);
        reports(chart);
        reads(chart);
        edge(chart, DEPLOYED, "deploy read red", new TaskMove.DeployRead(true)::equals, DEPLOY_CONFLICT);
        edge(chart, VERIFYING, "verified", new TaskMove.Verified(true)::equals, REVIEW_PENDING);
        edge(chart, VERIFYING, "verification failed", new TaskMove.Verified(false)::equals, IN_PROGRESS);
        return chart.build();
    }

    private static void stops(ChartBuilder<TaskMove, Object, TaskEffect> chart) {
        chart.region(STOP, StopKind.NONE);
        chart.onMatching(STOP, StopKind.NONE, "lift", new TaskMove.Lifted()::equals)
                .emit(new TaskEffect.ClearsStop()).to(StopKind.NONE);
        for (TaskStop.Trigger.Ends ends : TaskStop.Trigger.Ends.values()) {
            StopKind kind = StopKind.of(ends);
            chart.onMatching(STOP, StopKind.NONE, "stop ending on " + ends,
                            move -> move instanceof TaskMove.StopSet set && set.stop().trigger().ends() == ends)
                    .emitFor(move -> new TaskEffect.Stops(((TaskMove.StopSet) move).stop())).to(kind);
            chart.onMatching(STOP, kind, "lift", new TaskMove.Lifted()::equals)
                    .emit(new TaskEffect.ClearsStop()).to(StopKind.NONE);
            chart.onMatching(STOP, kind, ends + " ends it", new TaskMove.StopEnded(ends)::equals)
                    .emit(new TaskEffect.ClearsStop()).to(StopKind.NONE);
        }
    }

    private static void plans(ChartBuilder<TaskMove, Object, TaskEffect> chart) {
        chart.region(PLAN, PlanGate.OPEN);
        chart.onMatching(PLAN, PlanGate.OPEN, "autoplan", new TaskMove.Autoplanned()::equals)
                .emit(new TaskEffect.GatesPlan(PlanGate.CLOSED)).to(PlanGate.CLOSED);
        for (PlanGate from : EnumSet.of(PlanGate.OPEN, PlanGate.CLOSED)) {
            chart.onMatching(PLAN, from, "plan mode", move -> move instanceof TaskMove.PlanAsked asked
                            && asked.planMode())
                    .when((others, move) -> planMayComeFirst(others.get(LIFECYCLE)))
                    .emit(new TaskEffect.GatesPlan(PlanGate.REQUIRED)).to(PlanGate.REQUIRED);
        }
        chart.onMatching(PLAN, PlanGate.CLOSED, "no plan declared", new TaskMove.NoPlanDeclared()::equals)
                .emit(new TaskEffect.GatesPlan(PlanGate.OPEN)).to(PlanGate.OPEN);
        for (PlanGate from : EnumSet.of(PlanGate.CLOSED, PlanGate.REQUIRED)) {
            chart.onMatching(PLAN, from, "plan approved", new TaskMove.PlanApproved()::equals)
                    .emit(new TaskEffect.GatesPlan(PlanGate.OPEN)).to(PlanGate.OPEN);
        }
    }

    private static void says(ChartBuilder<TaskMove, Object, TaskEffect> chart) {
        chart.region(REPORT, AgentReport.PLAIN);
        for (AgentReport from : AgentReport.values()) {
            said(chart, from, "said on a status that holds its line", from.asks() ? AgentReport.PLAIN : from,
                    move -> move instanceof TaskMove.Said said && !said.means().asks(),
                    (others, move) -> HELD.contains(others.get(LIFECYCLE)));
            becomes(chart, from);
            if (from != AgentReport.PLAIN) {
                clears(chart, from);
            }
        }
    }

    private static void turns(ChartBuilder<TaskMove, Object, TaskEffect> chart) {
        chart.region(TURN, TurnKind.CLOSED);
        for (TurnKind from : TurnKind.values()) {
            relayed(chart, from);
            EdgeBuilder<TaskMove, Object, TaskEffect, TurnKind> closed = chart.onMatching(TURN, from, "turn closed",
                    new TaskMove.TurnClosed()::equals).emit(new TaskEffect.ClosesTurn());
            if (from == TurnKind.CLOSED) {
                closed.stay();
            } else {
                closed.to(TurnKind.CLOSED);
            }
            for (TurnKind restored : TurnKind.values()) {
                chart.onMatching(TURN, from, "turn restored " + restored, move -> move instanceof TaskMove.TurnRestored
                                undone && TurnKind.of(undone.before()) == restored)
                        .emitFor(move -> new TaskEffect.RestoresTurn(((TaskMove.TurnRestored) move).before()))
                        .to(restored);
            }
        }
    }

    private static void deploys(ChartBuilder<TaskMove, Object, TaskEffect> chart) {
        chart.region(DEPLOY, DeployPhase.NONE);
        for (DeployPhase from : DeployPhase.values()) {
            deployed(chart, from, "opened", DeployPhase.LANDING, step -> step instanceof DeployStep.Opened);
        }
        for (DeployPhase from : EnumSet.of(DeployPhase.LANDING, DeployPhase.HANDED)) {
            deployed(chart, from, "landed", DeployPhase.LANDING, step -> step instanceof DeployStep.Merged
                    || step instanceof DeployStep.Fixed || step instanceof DeployStep.Pushed
                    || step instanceof DeployStep.Blocked);
            deployed(chart, from, "conflicted", DeployPhase.HANDED, step -> step instanceof DeployStep.Conflicted);
        }
        for (DeployPhase from : DeployPhase.values()) {
            deployed(chart, from, "reverted", DeployPhase.REVERTING, step -> step instanceof DeployStep.Reverted);
        }
        deployed(chart, DeployPhase.HANDED, "discarded", DeployPhase.LANDING, step -> step instanceof DeployStep.Discarded);
        deployed(chart, DeployPhase.HANDED, "watched", DeployPhase.HANDED, step -> step instanceof DeployStep.Seen
                || step instanceof DeployStep.Told || step instanceof DeployStep.Held);
        chart.onMatching(DEPLOY, DeployPhase.HANDED, "conflict left", move -> !(move instanceof TaskMove.DeployStepped))
                .when((others, move) -> others.get(LIFECYCLE) == DEPLOY_CONFLICT
                        && land(DEPLOY_CONFLICT, move).filter(to -> to != DEPLOY_CONFLICT).isPresent())
                .emit(new TaskEffect.Deploys(new DeployStep.Cleared())).to(DeployPhase.LANDING);
        deployed(chart, DeployPhase.LANDING, "asked", DeployPhase.CHECK_ASKED, step -> step instanceof DeployStep.Asked);
    }

    private static void deployed(ChartBuilder<TaskMove, Object, TaskEffect> chart, DeployPhase from, String label,
                                 DeployPhase to, Predicate<DeployStep> matches) {
        EdgeBuilder<TaskMove, Object, TaskEffect, DeployPhase> edge = chart.onMatching(DEPLOY, from, label,
                        move -> move instanceof TaskMove.DeployStepped stepped && matches.test(stepped.step()))
                .emitFor(move -> new TaskEffect.Deploys(((TaskMove.DeployStepped) move).step()));
        if (from == to) {
            edge.stay();
        } else {
            edge.to(to);
        }
    }

    private static void relayed(ChartBuilder<TaskMove, Object, TaskEffect> chart, TurnKind from) {
        EdgeBuilder<TaskMove, Object, TaskEffect, TurnKind> relayed = chart.onMatching(TURN, from, "relayed",
                        move -> move instanceof TaskMove.Relayed)
                .emitFor(move -> new TaskEffect.OpensTurn(((TaskMove.Relayed) move).step(),
                        ((TaskMove.Relayed) move).digest(), ((TaskMove.Relayed) move).answered()));
        if (from == TurnKind.OPEN) {
            relayed.stay();
        } else {
            relayed.to(TurnKind.OPEN);
        }
    }

    private static void becomes(ChartBuilder<TaskMove, Object, TaskEffect> chart, AgentReport from) {
        for (AgentReport means : AgentReport.values()) {
            if (means != from) {
                said(chart, from, "said " + means, means, move -> move instanceof TaskMove.Said said
                        && said.means() == means, ALWAYS);
                said(chart, from, "stamped " + means, means, move -> move instanceof TaskMove.Stamped stamped
                        && stamped.means() == means, ALWAYS);
            }
        }
    }

    private static void clears(ChartBuilder<TaskMove, Object, TaskEffect> chart, AgentReport from) {
        said(chart, from, "verified", AgentReport.PLAIN, move -> move instanceof TaskMove.Verified, ALWAYS);
        said(chart, from, "deploy read moving the status", AgentReport.PLAIN,
                move -> move instanceof TaskMove.DeployRead, (others, move) -> land(others.get(LIFECYCLE), move)
                        .filter(to -> to != others.get(LIFECYCLE)).isPresent());
        if (!STANDING_THROUGH_A_READ.contains(from)) {
            said(chart, from, "read", AgentReport.PLAIN, move -> move instanceof TaskMove.Read, ALWAYS);
        }
    }

    private static void said(ChartBuilder<TaskMove, Object, TaskEffect> chart, AgentReport from, String label,
                             AgentReport to, Predicate<TaskMove> matches, Condition<TaskMove> when) {
        EdgeBuilder<TaskMove, Object, TaskEffect, AgentReport> edge = chart.onMatching(REPORT, from, label, matches)
                .when(when).emit(new TaskEffect.Reports(to));
        if (from == to) {
            edge.stay();
        } else {
            edge.to(to);
        }
    }

    /** Whether a plan can still come first for a task in {@code status}: it has written nothing from the code yet. */
    static boolean planMayComeFirst(TaskStatus status) {
        return land(status, new TaskMove.Reported(PLAN_PENDING, false)).isPresent();
    }

    private static void ran(ChartBuilder<TaskMove, Object, TaskEffect> chart) {
        ran(chart, TaskAction.SHIP, Outcome.Kind.OK, CI_POLLING, SHIP_FROM);
        ran(chart, TaskAction.SHIP, Outcome.Kind.RELAYED, SHIPPING, SHIP_FROM);
        ran(chart, TaskAction.APPROVE_PLAN, Outcome.Kind.RELAYED, IN_PROGRESS, EnumSet.of(PLAN_PENDING));
        ran(chart, TaskAction.ADD_REPO, Outcome.Kind.RELAYED, IN_PROGRESS,
                EnumSet.complementOf(EnumSet.of(PLAN_PENDING, DEPLOYED, DEPLOY_CONFLICT, REVERTED, DONE)));
        ran(chart, TaskAction.DEPLOY, Outcome.Kind.OK, DEPLOYED, DEPLOY_FROM);
        ran(chart, TaskAction.DEPLOY, Outcome.Kind.CONFLICT, DEPLOY_CONFLICT, DEPLOY_FROM);
        ran(chart, TaskAction.REVERT, Outcome.Kind.OK, REVERTED, OPEN);
        ran(chart, TaskAction.REVERT, Outcome.Kind.PARTIAL, DEPLOYED, EnumSet.complementOf(
                EnumSet.of(DEPLOY_CONFLICT, DONE)));
        ran(chart, TaskAction.DONE, Outcome.Kind.GONE, DONE, OPEN);
    }

    private static void ran(ChartBuilder<TaskMove, Object, TaskEffect> chart, TaskAction action,
                            Outcome.Kind outcome, TaskStatus to, Set<TaskStatus> from) {
        from.forEach(status -> edge(chart, status, action.id() + " " + outcome,
                new TaskMove.Ran(action, outcome)::equals, to));
    }

    private static void reports(ChartBuilder<TaskMove, Object, TaskEffect> chart) {
        for (TaskStatus to : REPORTED) {
            for (TaskStatus from : OPEN) {
                if (mayReport(from, to)) {
                    report(chart, from, to);
                }
            }
        }
    }

    private static boolean mayReport(TaskStatus from, TaskStatus to) {
        return (to != PLAN_PENDING || BEFORE_THE_CODE.contains(from))
                && (to != CI_POLLING || BEFORE_THE_VERDICT.contains(from));
    }

    private static void report(ChartBuilder<TaskMove, Object, TaskEffect> chart, TaskStatus from, TaskStatus to) {
        if (HELD.contains(from)) {
            reported(chart, from, to, from, false);
            return;
        }
        if (to == REVIEW_PENDING) {
            reported(chart, from, to, VERIFYING, true);
        }
        reported(chart, from, to, to, false);
    }

    private static void reported(ChartBuilder<TaskMove, Object, TaskEffect> chart, TaskStatus from, TaskStatus to,
                                 TaskStatus lands, boolean owedOnly) {
        edge(chart, from, "report " + to + (owedOnly ? " owing verification" : ""),
                move -> move instanceof TaskMove.Reported report && report.status() == to
                        && (!owedOnly || report.verificationOwed()), lands);
    }

    private static void reads(ChartBuilder<TaskMove, Object, TaskEffect> chart) {
        for (TaskStatus concluded : READ) {
            for (TaskStatus from : READ_FROM) {
                edge(chart, from, "read " + concluded, new TaskMove.Read(concluded)::equals, concluded);
            }
        }
    }

    private static void edge(ChartBuilder<TaskMove, Object, TaskEffect> chart, TaskStatus from, String label,
                             Predicate<TaskMove> matches, TaskStatus to) {
        EdgeBuilder<TaskMove, Object, TaskEffect, TaskStatus> edge = chart.onMatching(LIFECYCLE, from, label, matches)
                .emit(new TaskEffect.Lands(to));
        if (from == to) {
            edge.stay();
        } else {
            edge.to(to);
        }
    }
}
