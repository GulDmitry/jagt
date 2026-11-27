package dev.jagt.orchestrator.service.turn;

import org.jspecify.annotations.Nullable;

/**
 * What a turn end carries besides its task: whether a refusal already sent it on, whether it waits
 * on background work, and the text it ended with.
 */
public record TurnEnding(boolean sentOn, boolean pausedOnBackgroundWork, @Nullable String text) {}
