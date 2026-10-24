package dev.jagt.orchestrator.stand.invariant;

public interface Ancestry {

    boolean descends(String commit, String from);
}
