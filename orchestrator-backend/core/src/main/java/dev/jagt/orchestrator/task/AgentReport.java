package dev.jagt.orchestrator.task;

/** What a task's last report means; for a review round it decides whose move it is, all three ending at one status. */
public enum AgentReport {

    /** The agent stopped rather than guess; the question is the message. */
    QUESTION,
    /** The same, stopped on a right only the human can grant, which nobody standing in for them may answer. */
    PERMISSION,
    /** A round that changed no code: every comment was already handled or was answered with a push-back. */
    NO_CHANGES,
    /** The session works but a dependency it needs is down; the message names it and how to probe it. */
    WAIT,
    PLAIN;

    public boolean asks() {
        return this == QUESTION || this == PERMISSION;
    }
}
