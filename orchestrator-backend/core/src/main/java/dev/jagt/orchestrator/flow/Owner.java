package dev.jagt.orchestrator.flow;

/** Whose turn it is. */
public enum Owner {

    /** The agent is working; waiting is correct. */
    AGENT("agent"),
    /** You. Nothing moves until you act. */
    YOU("you"),
    /** The code host — a pipeline or a reviewer. */
    CI("code host"),
    /** Nobody: nothing is waiting on anyone — the task is closed, or its change is already live. */
    NOBODY("none");

    private final String label;

    Owner(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
