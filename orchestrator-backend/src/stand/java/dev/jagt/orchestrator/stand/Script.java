package dev.jagt.orchestrator.stand;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record Script(String ref, @Nullable String launchMode, String until, Duration timeout, Duration settle,
                     List<WorldEvent> events, boolean expectsDeployConflict, boolean conflictStepComes) {

    public static Script to(String ref, String until) {
        return new Script(ref, null, until, Duration.ofMinutes(30), Duration.ofSeconds(90), List.of(), false, false);
    }

    public Script planFirst() {
        return new Script(ref, "plan", until, timeout, settle, events, expectsDeployConflict, conflictStepComes);
    }

    public Script within(Duration limit) {
        return new Script(ref, launchMode, until, limit, settle, events, expectsDeployConflict, conflictStepComes);
    }

    public Script with(WorldEvent event) {
        List<WorldEvent> more = new ArrayList<>(events);
        more.add(event);
        return new Script(ref, launchMode, until, timeout, settle, List.copyOf(more), expectsDeployConflict,
                conflictStepComes);
    }

    public Script expectingDeployConflict(boolean stepComes) {
        return new Script(ref, launchMode, until, timeout, settle, events, true, stepComes);
    }
}
