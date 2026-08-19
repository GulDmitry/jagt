package dev.jagt.orchestrator.task;

/**
 * Who asked for a change — the question a task's history could not answer: it recorded that a task moved to
 * SHIPPING, never whether a human typed it, clicked it, said it in words, or an agent reported it.
 */
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
