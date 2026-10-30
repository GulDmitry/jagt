package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.statechart.Chart;
import dev.jagt.statechart.ChartBuilder;
import dev.jagt.statechart.EdgeBuilder;
import dev.jagt.statechart.RegionId;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static dev.jagt.orchestrator.task.TaskStatus.*;

/** The lifecycle region: one edge per status move, and the only place that says where a move leads. */
final class TaskChart {

    private static final RegionId<TaskStatus> LIFECYCLE = new RegionId<>("lifecycle", TaskStatus.class);

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

    private static final Chart<TaskMove, Object, TaskEffect> CHART = build();

    private TaskChart() {
    }

    static Chart<TaskMove, Object, TaskEffect> chart() {
        return CHART;
    }

    /** What {@code move} does to a task at {@code from}, in order; empty where the chart has no edge for it. */
    static List<TaskEffect> effects(TaskStatus from, TaskMove move) {
        return CHART.step(CHART.configuration(Map.of(LIFECYCLE.name(), from.name())), NO_FACTS, move).effects();
    }

    /** Where {@code move} leads a task at {@code from}, or empty where the chart has no edge for it. */
    static Optional<TaskStatus> land(TaskStatus from, TaskMove move) {
        return effects(from, move).stream().filter(TaskEffect.Lands.class::isInstance)
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

    static Set<TaskStatus> reads() {
        return EnumSet.copyOf(READ);
    }

    private static Chart<TaskMove, Object, TaskEffect> build() {
        ChartBuilder<TaskMove, Object, TaskEffect> chart = Chart.builder();
        chart.region(LIFECYCLE, NEW, Set.of(DONE));
        chart.onExit(LIFECYCLE, DEPLOY_CONFLICT, new TaskEffect.ClearsConflict());
        ran(chart);
        reports(chart);
        reads(chart);
        edge(chart, DEPLOYED, "deploy read red", new TaskMove.DeployRead(true)::equals, DEPLOY_CONFLICT);
        edge(chart, VERIFYING, "verified", new TaskMove.Verified(true)::equals, REVIEW_PENDING);
        edge(chart, VERIFYING, "verification failed", new TaskMove.Verified(false)::equals, IN_PROGRESS);
        return chart.build();
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
