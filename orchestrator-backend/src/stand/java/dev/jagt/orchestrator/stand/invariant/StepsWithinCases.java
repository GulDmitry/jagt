package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.config.CaseBook;
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
                .filter(step -> !book.sends(step, run.statusAt(relay.task(), relay.at())))
                .map(step -> new Violation(NAME, relay.at(), step + " at " + run.statusAt(relay.task(), relay.at())
                        + ": " + relay.line())))
                .toList();
    }
}
