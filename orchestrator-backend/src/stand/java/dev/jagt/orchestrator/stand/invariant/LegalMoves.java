package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.flow.Facts;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

public final class LegalMoves {

    private static final List<Facts> EVERY_FACT = IntStream.range(0, 64).mapToObj(bits -> new Facts(bit(bits, 0),
            bit(bits, 1), () -> bit(bits, 2), bit(bits, 3), bit(bits, 4), bit(bits, 5))).toList();

    private LegalMoves() {
    }

    public static boolean allows(TaskStatus from, TaskStatus to) {
        return from == to || byAction(from, to) || byReport(from, to) || byRead(from, to);
    }

    public static boolean reaches(TaskStatus from, TaskStatus to) {
        Set<TaskStatus> seen = EnumSet.of(from);
        Deque<TaskStatus> open = new ArrayDeque<>(seen);
        while (!open.isEmpty()) {
            TaskStatus at = open.pop();
            for (TaskStatus next : TaskStatus.values()) {
                if (allows(at, next) && seen.add(next)) {
                    open.push(next);
                }
            }
        }
        return seen.contains(to);
    }

    private static boolean byAction(TaskStatus from, TaskStatus to) {
        for (TaskAction action : TaskAction.values()) {
            boolean allowed = EVERY_FACT.stream().anyMatch(facts -> FlowRules.allows(from, action, facts));
            for (Outcome.Kind kind : Outcome.Kind.values()) {
                if (allowed && FlowRules.next(from, action, kind).equals(Optional.of(to))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean byReport(TaskStatus from, TaskStatus to) {
        for (TaskStatus reported : TaskStatus.values()) {
            boolean accepted = FlowRules.refusedReport(from, reported).isEmpty();
            if (accepted && (FlowRules.reported(from, reported, false) == to || FlowRules.reported(from, reported, true) == to)) {
                return true;
            }
        }
        return false;
    }

    private static boolean byRead(TaskStatus from, TaskStatus to) {
        return FlowRules.reads().stream().anyMatch(read -> FlowRules.readLands(from, read) == to);
    }

    private static boolean bit(int bits, int index) {
        return (bits >> index & 1) == 1;
    }
}
