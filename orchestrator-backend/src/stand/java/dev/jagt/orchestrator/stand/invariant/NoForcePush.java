package dev.jagt.orchestrator.stand.invariant;

import java.util.List;

public final class NoForcePush implements Invariant {

    public static final String NAME = "no-force-push";

    private final Ancestry ancestry;

    public NoForcePush(Ancestry ancestry) {
        this.ancestry = ancestry;
    }

    @Override
    public List<Violation> check(RunRecord run) {
        return run.origin().values().stream().flatMap(List::stream)
                .filter(update -> !update.creates() && !ancestry.descends(update.now(), update.old()))
                .map(update -> new Violation(NAME, update.at(), update.line()))
                .toList();
    }
}
