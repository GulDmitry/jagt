package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** The chart states a task must be in, one set per region; a region not named admits every state, the stop region none but NONE. */
public record Watch(Set<TaskStatus> statuses, Set<StopKind> stops, Set<AgentReport> reports, Set<TurnKind> turns,
                    Set<PlanGate> plans) {

    public Watch {
        statuses = Set.copyOf(statuses);
        stops = Set.copyOf(stops);
        reports = Set.copyOf(reports);
        turns = Set.copyOf(turns);
        plans = Set.copyOf(plans);
    }

    public static Watch defaults() {
        return new Watch(EnumSet.allOf(TaskStatus.class), EnumSet.of(StopKind.NONE),
                EnumSet.allOf(AgentReport.class), EnumSet.allOf(TurnKind.class), EnumSet.allOf(PlanGate.class));
    }

    public Watch withStatuses(TaskStatus... in) {
        return new Watch(Set.of(in), stops, reports, turns, plans);
    }

    public Watch withStatuses(Predicate<TaskStatus> in) {
        return new Watch(Arrays.stream(TaskStatus.values()).filter(in).collect(
                Collectors.toCollection(() -> EnumSet.noneOf(TaskStatus.class))), stops, reports,
                turns, plans);
    }

    public Watch withStops(StopKind... in) {
        return new Watch(statuses, Set.of(in), reports, turns, plans);
    }

    public Watch withReports(Predicate<AgentReport> in) {
        return new Watch(statuses, stops, Arrays.stream(AgentReport.values()).filter(in).collect(
                Collectors.toCollection(() -> EnumSet.noneOf(AgentReport.class))), turns, plans);
    }

    public Watch withTurns(TurnKind... in) {
        return new Watch(statuses, stops, reports, Set.of(in), plans);
    }

    public Watch withPlans(PlanGate... in) {
        return new Watch(statuses, stops, reports, turns, Set.of(in));
    }

    public boolean admits(TaskState task) {
        return statuses.contains(task.status()) && stops.contains(StopKind.of(task.stop()))
                && reports.contains(task.report()) && turns.contains(TurnKind.of(task.turnOrNone()))
                && plans.contains(task.planGate());
    }
}
