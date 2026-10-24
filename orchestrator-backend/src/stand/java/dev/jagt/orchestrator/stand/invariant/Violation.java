package dev.jagt.orchestrator.stand.invariant;

import java.time.Instant;

public record Violation(String invariant, Instant at, String evidence) {

    @Override
    public String toString() {
        return invariant + " at " + at + ": " + evidence;
    }
}
