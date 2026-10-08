package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStatus;
/**
 * What the last review round left behind: what the agent said about it, whether replies it drafted are still
 * waiting, and whether the Master has yet to read it. All three decide the human's next move, and none is in
 * {@link TaskStatus}.
 */
public record RoundState(AgentReport report, boolean draftedReplies, boolean masterReading) {

    public static final RoundState NONE = new RoundState(AgentReport.PLAIN, false, false);

    public RoundState(AgentReport report, boolean draftedReplies) {
        this(report, draftedReplies, false);
    }

    public static RoundState of(String message, boolean draftedReplies) {
        return new RoundState(AgentReport.of(message), draftedReplies, false);
    }

    public RoundState withMasterReading(boolean masterReading) {
        return new RoundState(report, draftedReplies, masterReading);
    }
}
