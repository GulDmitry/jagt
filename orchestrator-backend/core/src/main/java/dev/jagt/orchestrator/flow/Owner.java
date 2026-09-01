package dev.jagt.orchestrator.flow;

/** Whose turn it is. */
public enum Owner {

    /** The agent is working; waiting is correct. */
    AGENT,
    /** You. Nothing moves until you act. */
    YOU,
    /** The code host — a pipeline or a reviewer. */
    CI,
    /** Nobody: nothing is waiting on anyone — the task is closed, or its change is already live. */
    NOBODY
}
