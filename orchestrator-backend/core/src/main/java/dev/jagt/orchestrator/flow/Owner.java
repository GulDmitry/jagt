package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Turn;

/** Whose turn it is. */
public enum Owner {

    /** The agent is working; waiting is correct. */
    AGENT,
    /** You. Nothing moves until you act. */
    YOU,
    /** The code host — a pipeline or a reviewer. */
    CI,
    /** Nobody: nothing is waiting on anyone — the task is closed, or its change is already live. */
    NOBODY;

    /** The Master's turn reads as the agent's: it is a machine at work, and nothing asks the human. */
    public static Owner of(Turn.Holder holder) {
        return switch (holder) {
            case Turn.Holder.Role _ -> AGENT;
            case Turn.Holder.Fixed fixed -> switch (fixed) {
                case SESSION, ARBITER -> AGENT;
                case HUMAN -> YOU;
                case CI -> CI;
                case NOBODY -> NOBODY;
            };
        };
    }
}
