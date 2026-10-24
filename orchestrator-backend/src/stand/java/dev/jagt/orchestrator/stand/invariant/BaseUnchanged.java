package dev.jagt.orchestrator.stand.invariant;

import java.time.Instant;
import java.util.List;

public final class BaseUnchanged implements Invariant {

    public static final String NAME = "base-unchanged";

    @Override
    public List<Violation> check(RunRecord run) {
        List<RunRecord.RefUpdate> updates = run.origin().getOrDefault(RunRecord.BASE, List.of());
        if (updates.isEmpty()) {
            return List.of(new Violation(NAME, Instant.EPOCH, "origin.git holds no reflog of " + RunRecord.BASE
                    + ", so nothing shows it unchanged"));
        }
        return updates.stream().skip(1).map(update -> new Violation(NAME, update.at(), update.line())).toList();
    }
}
