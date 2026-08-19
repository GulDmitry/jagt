package dev.jagt.orchestrator.flow;

import java.util.function.BooleanSupplier;

/**
 * What a rule may ask about a task besides its status.
 *
 * <p>{@code agentLive} is a supplier because answering it costs a process probe: a projection built for every row
 * of a dashboard passes one that says no, while the gate that is about to act passes the real thing. One rule,
 * two prices.
 */
public record Facts(boolean hasReviewRequest, BooleanSupplier agentLive) {

    public static Facts projected(boolean hasReviewRequest) {
        return new Facts(hasReviewRequest, () -> false);
    }
}
