package dev.jagt.orchestrator.task;

/** Who asked for a status change. */
public enum ActionOrigin {

    CONSOLE,
    BOARD,
    /** Free text a model mapped to a command, whichever surface it was typed into. */
    PALETTE,
    /** An MCP call: a sub-agent reporting its own progress, or a session working on jagt itself. */
    MCP,
    AUTO_REVIEW;

    public String label() {
        return name().toLowerCase().replace('_', '-');
    }
}
