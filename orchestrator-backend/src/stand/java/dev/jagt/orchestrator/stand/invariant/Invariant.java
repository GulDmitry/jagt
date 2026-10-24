package dev.jagt.orchestrator.stand.invariant;

import java.util.List;

public interface Invariant {

    List<Violation> check(RunRecord run);
}
