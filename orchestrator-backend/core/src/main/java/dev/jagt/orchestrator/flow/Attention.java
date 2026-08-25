package dev.jagt.orchestrator.flow;

/**
 * How loudly a task asks for the human. {@link Owner} answers WHO and {@link Move#ask} WHICH act — this answers
 * only whether the card is an interruption. One value, because the badge, the header count and the own-move
 * filter are one question asked three times, and a tier that only some of them read means nothing.
 *
 * <p>{@code NONE} exactly when the owner is not {@link Owner#YOU}.
 */
public enum Attention {

    /**
     * Nothing moves until a human acts: a session that stopped or asked, a round back from review, a red run, a
     * conflict, a round nothing will read again. What jagt exists to surface.
     */
    REQUIRED("action required"),
    /** The task is in a good state and the next move is theirs whenever they want it. Never an interruption. */
    OPTIONAL("your move"),
    /** Not the human's turn at all. */
    NONE(null);

    private final String label;

    Attention(String label) {
        this.label = label;
    }

    /** The tier in words, for a surface with no colour to spend on saying it; null when it says nothing. */
    public String label() {
        return label;
    }
}
