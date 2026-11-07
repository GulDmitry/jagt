package dev.jagt.orchestrator.flow;

import java.util.Map;

/**
 * What the chart's derived regions are read from: the record's own facts that no stored region
 * holds.
 */
record ChartFacts(boolean stampedAsk, Map<String, CheckerPhase> beneath, Facts facts) {

  CheckerPhase beneath(String checker) {
    return beneath.getOrDefault(checker, CheckerPhase.NONE);
  }
}
