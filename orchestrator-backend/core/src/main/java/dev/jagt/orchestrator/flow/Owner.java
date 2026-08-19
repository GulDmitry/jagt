package dev.jagt.orchestrator.flow;

/**
 * Whose turn it is. The single most useful fact about a task and the one the old prose hint buried: a human
 * scanning a board wants to know what is waiting for THEM before anything else.
 */
public enum Owner {

    /** The agent is working; waiting is correct. */
    AGENT("agent"),
    /** You. Nothing moves until you act. */
    YOU("you"),
    /** The code host — a pipeline or a reviewer. */
    CI("ci"),
    /** Nobody: the task is closed. */
    NOBODY("nobody");

    private final String label;

    Owner(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
