package dev.jagt.orchestrator.flow.capability;

import dev.jagt.orchestrator.flow.session.StopHold;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import lombok.With;
import org.jspecify.annotations.Nullable;

/** What a rule may ask about a task besides its status. */
@With
public record Facts(Delivery delivery, Hold hold, Workspace workspace, Reach reach) {

  private static final Set<TaskStop.Trigger> ESCALATING =
      EnumSet.of(
          TaskStop.Trigger.STALLED,
          TaskStop.Trigger.SHIPS,
          TaskStop.Trigger.ROUNDS,
          TaskStop.Trigger.UNCHANGED_FAILURE,
          TaskStop.Trigger.ACCEPTANCE_FAILED);

  @With
  public record Delivery(
      boolean hasReviewRequest,
      boolean requestMerged,
      boolean liveDeploy,
      boolean revertConflict,
      boolean shippedWithoutRequest,
      BooleanSupplier mergedNow) {

    public static Delivery defaults() {
      return new Delivery(false, false, false, false, false, () -> false);
    }
  }

  /**
   * {@code agentLive} is a supplier because answering it costs a process probe: a projection buil
   * for every row of a dashboard passes one that says no, while the gate that is about to ac passes
   * the real thing. One rule, two prices.
   */
  @With
  public record Hold(
      boolean stopped,
      boolean stopIsTheArbiters,
      boolean tierAbove,
      boolean planClosed,
      BooleanSupplier agentLive) {

    public static Hold defaults() {
      return new Hold(false, false, false, false, () -> false);
    }
  }

  @With
  public record Workspace(
      boolean gitStateChanged,
      boolean repoRequested,
      boolean severalRepos,
      Supplier<Optional<String>> distrust,
      Supplier<Optional<String>> findingsOpen) {

    public static Workspace defaults() {
      return new Workspace(false, false, false, Optional::empty, Optional::empty);
    }
  }

  /**
   * What the configuration lets jagt offer, whatever the task is; {@code sessionHeld} is why the
   * task's session cannot start, null where nothing holds it.
   */
  @With
  public record Reach(
      boolean scenarios,
      boolean shellEscape,
      boolean requireApproval,
      @Nullable String sessionHeld) {

    public static Reach defaults() {
      return new Reach(false, false, false, null);
    }
  }

  public static Facts defaults() {
    return new Facts(Delivery.defaults(), Hold.defaults(), Workspace.defaults(), Reach.defaults());
  }

  /**
   * A stop that a heavier model could answer stands and the session's ladder has a model above the
   * one it runs on.
   */
  public static boolean tierAbove(TaskState task) {
    TaskStop stop = task.work().stop();
    return stop != null
        && ESCALATING.contains(stop.trigger())
        && task.work().tier() != null
        && task.work().tier().higher();
  }

  /** A recorded merge is one no revert has taken out yet. */
  public static boolean deployLive(TaskState task) {
    return task.work().deployment() != null && task.work().deployment().live();
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
            .withStopped(task.work().stopped())
            .withTierAbove(tierAbove(task))
            .withPlanClosed(task.gate().planGate().closed())
            .withStopIsTheArbiters(StopHold.mayBeLifted(task) && !StopHold.asked(task)),
        Workspace.defaults()
            .withGitStateChanged(task.gate().gitStateChanged())
            .withRepoRequested(task.work().asksForRepository())
            .withSeveralRepos(task.repos().size() > 1),
        Reach.defaults());
  }
}
