package dev.jagt.orchestrator.flow;

/**
 * How loudly a task asks for the human, and in what words. {@link Owner} answers WHO — this answers whether the
 * card is an interruption. One value, because the badge, the header count and the own-move filter are one
 * question asked three times, and a tier that only some of them read is a badge that means nothing.
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

    /** The words both surfaces render; null when there is nothing to say. */
    public String label() {
        return label;
    }
}
