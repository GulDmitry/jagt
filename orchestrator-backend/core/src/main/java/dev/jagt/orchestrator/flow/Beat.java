package dev.jagt.orchestrator.flow;

/**
 * The last sign of a step in flight: how many of its retries and restarts a missed beat spent, and
 * how many red reads it has had.
 */
record Beat(ProgressStep step, long since, long lastAt, int attempts, int reds) {}
