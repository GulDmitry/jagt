package dev.jagt.orchestrator.flow;

/**
 * What the last review round left behind: what the agent said about it, and whether replies it drafted are
 * still waiting. Both decide the human's next move, and neither is in {@link TaskStatus}.
 */
public record RoundState(AgentReport report, boolean draftedReplies) {

    public static final RoundState NONE = new RoundState(AgentReport.PLAIN, false);

    public static RoundState of(String message, boolean draftedReplies) {
        return new RoundState(AgentReport.of(message), draftedReplies);
    }
}
