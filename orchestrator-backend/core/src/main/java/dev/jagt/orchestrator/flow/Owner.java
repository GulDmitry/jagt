package dev.jagt.orchestrator.flow;

/** Whose turn it is. */
public enum Owner {

    /** The agent is working; waiting is correct. */
    AGENT("agent"),
    /** You. Nothing moves until you act. */
    YOU("you"),
    /** The code host — a pipeline or a reviewer. */
    CI("code host"),
    /** Nobody: the task is closed. */
    NOBODY("none");

    private final String label;

    Owner(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
