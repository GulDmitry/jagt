package dev.jagt.orchestrator.flow;

/**
 * How loudly a task asks for the human: only whether the card is an interruption. One value, because the badge, the
 * header count and the own-move filter are one question asked three times. {@code NONE} exactly when the owner is
 * not {@link Owner#YOU}.
 */
public enum Attention {

    /** Nothing moves until a human acts. */
    REQUIRED,
    /** The task is in a good state and the next move is theirs whenever they want it. Never an interruption. */
    OPTIONAL,
    /** Not the human's turn at all. */
    NONE
}
