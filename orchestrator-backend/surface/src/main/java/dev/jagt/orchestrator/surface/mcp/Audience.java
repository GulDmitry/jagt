package dev.jagt.orchestrator.surface.mcp;

/** Who a tool is for: what it is listed to and what it answers are the same question. */
public enum Audience {

    ANYONE,
    /** Writes outside the caller's own worktree, so a sub-agent is neither shown it nor let through. */
    MASTER;

    public boolean admits(String callerTaskId) {
        return this == ANYONE || callerTaskId == null;
    }
}
