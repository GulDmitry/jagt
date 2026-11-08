package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import lombok.With;

/** What a rule may ask about a task besides its status. */
@With
public record Facts(Delivery delivery, Hold hold, Workspace workspace) {

  private static final Set<TaskStop.Trigger> ESCALATING =
      EnumSet.of(
          TaskStop.Trigger.STALLED,
          TaskStop.Trigger.SHIPS,
          TaskStop.Trigger.ROUNDS,
          TaskStop.Trigger.UNCHANGED_FAILURE,
          TaskStop.Trigger.QA_FAILED);

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
   * {@code agentLive} is a supplier because answering it costs a process probe: a projection buil
   * for every row of a dashboard passes one that says no, while the gate that is about to ac
   * passes the real thing. One rule, two prices.
   */
  @With
  public record Hold(
      boolean stopped, boolean stopIsTheMasters, boolean tierAbove, BooleanSupplier agentLive) {

    public static Hold defaults() {
      return new Hold(false, false, false, () -> false);
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

  /**
   * A stop that a heavier model could answer stands and the session's ladder has a model above the
   * one it runs on.
   */
  public static boolean tierAbove(TaskState task) {
    TaskStop stop = task.stop();
    return stop != null
        && ESCALATING.contains(stop.trigger())
        && task.tier() != null
        && task.tier().higher();
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
            .withTierAbove(tierAbove(task))
            .withStopIsTheMasters(StopHold.mayBeLifted(task) && !StopHold.asked(task)),
        Workspace.defaults()
            .withGitStateChanged(task.gitStateChanged())
            .withRepoRequested(task.asksForRepository())
            .withSeveralRepos(task.repos().size() > 1));
  }
}
