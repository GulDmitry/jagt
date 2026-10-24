package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.task.ActionOrigin;
import java.util.List;

public final class MissStopsTheMaster implements Invariant {

    public static final String NAME = "miss-stops-the-master";

    @Override
    public List<Violation> check(RunRecord run) {
        return run.misses().stream().flatMap(miss -> run.history(miss.task()).stream()
                .filter(step -> step.at().isAfter(miss.at()) && step.origin().equals(ActionOrigin.MASTER.name()))
                .map(step -> new Violation(NAME, step.at(), miss.task() + " moved to " + step.status()
                        + " by MASTER after the miss " + miss.line())))
                .toList();
    }
}
