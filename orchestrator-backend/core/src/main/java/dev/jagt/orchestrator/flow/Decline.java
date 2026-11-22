package dev.jagt.orchestrator.flow;

/**
 * The sentence a press is declined with; {@code quiet} answers it as nothing to do, not a refusal.
 */
public record Decline(String why, boolean quiet) {}
