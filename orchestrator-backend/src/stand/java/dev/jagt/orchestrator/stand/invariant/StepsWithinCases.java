package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.config.CaseBook;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;

public final class StepsWithinCases implements Invariant {

    public static final String NAME = "steps-within-cases";

    private final CaseBook book;

    public StepsWithinCases(CaseBook book) {
        this.book = book;
    }

    @Override
    public List<Violation> check(RunRecord run) {
        return run.timeline().relays().stream().flatMap(relay -> relay.steps().stream()
                .filter(step -> !book.sends(step, opened(run, relay)))
                .map(step -> new Violation(NAME, relay.at(), step + " at " + opened(run, relay) + ": " + relay.line())))
                .toList();
    }

    private static TaskStatus opened(RunRecord run, RunRecord.Relay relay) {
        return relay.status() != null ? relay.status() : run.statusAt(relay.task(), relay.at());
    }
}
