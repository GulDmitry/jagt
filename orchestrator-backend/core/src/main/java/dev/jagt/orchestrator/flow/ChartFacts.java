package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.capability.Facts;
import java.util.Map;

/** What the statechart's data are read from: the record's own facts that no region holds. */
record ChartFacts(boolean stampedAsk, Map<String, CheckerPhase> beneath, Facts facts) {

  CheckerPhase beneath(String checker) {
    return beneath.getOrDefault(checker, CheckerPhase.NONE);
  }
}
