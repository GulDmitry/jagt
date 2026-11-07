package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskState;
import java.util.function.BooleanSupplier;
import lombok.With;

/** What a rule may ask about a task besides its status. */
@With
public record Facts(Delivery delivery, Hold hold, Workspace workspace) {

  @With
  public record Delivery(
      boolean hasReviewRequest,
      boolean requestMerged,
      boolean liveDeploy,
      boolean revertConflict,
      boolean shippedWithoutRequest) {

    public static Delivery defaults() {
      return new Delivery(false, false, false, false, false);
    }
  }

  /**
   * {@code agentLive} is a supplier because answering it costs a process probe: a projection built
   * for every row of a dashboard passes one that says no, while the gate that is about to act
   * passes the real thing. One rule, two prices.
   */
  @With
  public record Hold(boolean stopped, boolean stopIsTheMasters, BooleanSupplier agentLive) {

    public static Hold defaults() {
      return new Hold(false, false, () -> false);
    }
  }

  @With
  public record Workspace(boolean gitStateChanged, boolean repoRequested, boolean severalRepos) {

    public static Workspace defaults() {
      return new Workspace(false, false, false);
    }
  }

  public static Facts defaults() {
    return new Facts(Delivery.defaults(), Hold.defaults(), Workspace.defaults());
  }

  /** A recorded merge is one no revert has taken out yet. */
  public static boolean deployLive(TaskState task) {
    return task.deployment() != null && task.deployment().live();
  }

  /** Whether the conflict the task stands in is a revert's. */
  public static boolean revertConflicted(TaskState task) {
    return task.currentConflict()
        .filter(conflict -> conflict.cause() == Deployment.Cause.REVERT)
        .isPresent();
  }

  public static Facts projected(TaskState task) {
    return new Facts(
        Delivery.defaults()
            .withHasReviewRequest(task.hasReviewRequest())
            .withLiveDeploy(deployLive(task))
            .withRequestMerged(task.requestsMerged())
            .withRevertConflict(revertConflicted(task)),
        Hold.defaults()
            .withStopped(task.stopped())
            .withStopIsTheMasters(StopHold.mayBeLifted(task) && !StopHold.asked(task)),
        Workspace.defaults()
            .withGitStateChanged(task.gitStateChanged())
            .withRepoRequested(task.asksForRepository())
            .withSeveralRepos(task.repos().size() > 1));
  }
}
