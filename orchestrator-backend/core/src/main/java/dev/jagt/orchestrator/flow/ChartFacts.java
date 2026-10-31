package dev.jagt.orchestrator.flow;

/** What the chart's derived regions are read from: the record's own facts that no stored region holds. */
record ChartFacts(boolean stampedAsk, QaPhase beneathQa, Facts facts) {
}
