package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.capability.Outcome;

/** What a press answered: the sentence, and how the work ended. */
public record Pressed(String message, Outcome.Kind kind) {}
