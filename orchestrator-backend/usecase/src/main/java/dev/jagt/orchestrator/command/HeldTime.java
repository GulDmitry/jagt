package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.task.StatusChange;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class HeldTime {

    private HeldTime() {
    }

    static Map<Owner, Long> byOwner(List<StatusChange> history, long until) {
        Map<Owner, Long> held = new EnumMap<>(Owner.class);
        for (int step = 0; step < history.size(); step++) {
            StatusChange change = history.get(step);
            long end = step + 1 < history.size() ? history.get(step + 1).at() : until;
            held.merge(Move.ownerOf(change.status()), Math.max(0, end - change.at()), Long::sum);
        }
        return held;
    }
}
