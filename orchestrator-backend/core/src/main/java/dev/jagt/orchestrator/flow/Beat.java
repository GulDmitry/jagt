package dev.jagt.orchestrator.flow;

/** The last sign of a step in flight, and how many of its retries and restarts are spent. */
record Beat(ProgressStep step, long since, long lastAt, int attempts) {}
