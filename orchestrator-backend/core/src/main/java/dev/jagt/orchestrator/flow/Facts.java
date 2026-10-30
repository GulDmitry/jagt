package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskState;
import lombok.With;

import java.util.function.BooleanSupplier;

/**
 * What a rule may ask about a task besides its status.
 *
 * <p>{@code agentLive} is a supplier because answering it costs a process probe: a projection built for every row
 * of a dashboard passes one that says no, while the gate that is about to act passes the real thing. One rule,
 * two prices.
 */
@With
public record Facts(boolean hasReviewRequest, boolean liveDeploy, BooleanSupplier agentLive,
                    boolean gitStateChanged, boolean stopped, boolean repoRequested,
                    boolean requestMerged, boolean revertConflict) {

    public static Facts defaults() {
        return new Facts(false, false, () -> false, false, false, false, false, false);
    }

    /** A recorded merge is one no revert has taken out yet. */
    public static boolean deployLive(TaskState task) {
        return task.deployment() != null && task.deployment().live();
    }

    /** Whether the conflict the task stands in is a revert's. */
    public static boolean revertConflicted(TaskState task) {
        return task.currentConflict().filter(conflict -> conflict.cause() == Deployment.Cause.REVERT).isPresent();
    }

    public static Facts projected(TaskState task) {
        return defaults()
                .withHasReviewRequest(task.hasReviewRequest())
                .withLiveDeploy(deployLive(task))
                .withGitStateChanged(task.gitStateChanged())
                .withStopped(task.stopped())
                .withRepoRequested(task.asksForARepository())
                .withRequestMerged(task.requestsMerged())
                .withRevertConflict(revertConflicted(task));
    }
}
