package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.RequestlessShip;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The ONE place a task's status is decided and written, whoever asked: no doer names a status, and
 * a refusal is answered with a sentence instead of a git error three layers down.
 */
@RequiredArgsConstructor
public class FlowEngine {

  private final TaskStore tasks;
  private final Capabilities capabilities;
  private final AgentPresence agents;
  private final Clock clock;
  private final RequestlessShip requestless;

  /** Runs {@code action} on the task and returns what to show the human. */
  public String run(String taskIdOrAlias, TaskAction action) {
    return run(taskIdOrAlias, action, null);
  }

  /**
   * As above, on a {@code project} the human named: the capability vets it and does the work on it,
   * at any status {@code action} can be taken, and it is refused with nothing written where it
   * cannot be taken.
   */
  public String run(String taskIdOrAlias, TaskAction action, @Nullable String project) {
    String taskId = tasks.canonicalTaskId(taskIdOrAlias);
    TaskState task = tasks.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskIdOrAlias));
    Facts read = facts(task, taskId);
    Facts facts =
        project == null
            ? read
            : capabilities.of(action).map(named -> named.naming(read)).orElse(read);
    if (!FlowRules.allows(task.status(), action, facts)) {
      Facts projected = Facts.projected(task);
      throw new Refusal(
          Refusal.Code.ACTION_NOT_AVAILABLE,
          action.label()
              + " is not available for "
              + taskId
              + " (it is "
              + task.status()
              + " — "
              + Move.forTask(
                      task.status(),
                      projected.withDelivery(
                          projected.delivery().withShippedWithoutRequest(requestless.stands(task))),
                      dev.jagt.orchestrator.flow.RoundState.of(task.report(), false),
                      SessionPhase.silent(task))
                  .hint()
              + ")");
    }
    TaskCapability capability =
        capabilities
            .of(action)
            .orElseThrow(
                () ->
                    new IllegalStateException("No capability declared for `" + action.id() + "`"));
    if (project != null) {
      named(taskId, task, action, project, capability);
    }
    return apply(taskId, task.status(), action, wrapped(taskId, action, capability, project));
  }

  private static void named(
      String taskId, TaskState task, TaskAction action, String project, TaskCapability capability) {
    if (!action.takesProject()) {
      throw Refusal.byState(action.id() + " takes no project");
    }
    if (FlowRules.next(task.status(), action, Outcome.Kind.RELAYED).isEmpty()) {
      throw new Refusal(
          Refusal.Code.ACTION_NOT_AVAILABLE,
          action.label()
              + " cannot name a project for "
              + taskId
              + " (it is "
              + task.status()
              + ")");
    }
    capability.vet(taskId, task, project);
  }

  /**
   * The work, inside whatever an install declared around this verb; the capability itself is
   * innermost.
   */
  private Outcome wrapped(
      String taskId, TaskAction action, TaskCapability capability, @Nullable String project) {
    Supplier<Outcome> work =
        project == null ? () -> capability.run(taskId) : () -> capability.run(taskId, project);
    for (CapabilityInterceptor interceptor : capabilities.around(action).reversed()) {
      Supplier<Outcome> inner = work;
      work = () -> interceptor.around(taskId, inner);
    }
    return work.get();
  }

  private String apply(String taskId, TaskStatus was, TaskAction action, Outcome outcome) {
    Optional<TaskStatus> next = FlowRules.next(was, action, outcome.kind());
    if (next.isPresent() || outcome.stamp() != null) {
      TaskMove move = new TaskMove.Ran(action, outcome.kind());
      // Recorded even when the status is unchanged: a second round onto the same request happened.
      tasks.updateTask(
          taskId,
          task -> {
            String line = outcome.stamp() == null ? task.message() : outcome.stamp();
            long at = clock.millis();
            TaskState written =
                next.isPresent()
                    ? TaskReducer.reduce(task, move, line, true, at)
                    : TaskReducer.stayed(task, line, true, at);
            return outcome.stamp() == null
                ? written
                : TaskReducer.stamped(written, outcome.stampMeans());
          });
    }
    if (outcome.kind().refuses()) {
      throw new IllegalStateException(outcome.message(), outcome.cause());
    }
    return outcome.message();
  }

  private boolean live(String taskId) {
    try {
      return agents.agentLive(taskId);
    } catch (AgentPresence.Unread unread) {
      throw Refusal.byState(
          "Cannot tell whether the session runs, its window was not read: " + unread.getMessage());
    }
  }

  /** The real liveness probe, unlike the projection's: this one is about to act on the answer. */
  private Facts facts(TaskState task, String taskId) {
    return new Facts(
        Facts.Delivery.defaults()
            .withHasReviewRequest(task.hasReviewRequest())
            .withLiveDeploy(Facts.deployLive(task))
            .withRequestMerged(task.requestsMerged())
            .withRevertConflict(Facts.revertConflicted(task))
            .withShippedWithoutRequest(requestless.stands(task)),
        Facts.Hold.defaults()
            .withAgentLive(() -> live(taskId))
            .withStopped(task.stopped())
            .withTierAbove(Facts.tierAbove(task)),
        Facts.Workspace.defaults()
            .withGitStateChanged(task.gitStateChanged())
            .withRepoRequested(task.asksForRepository())
            .withSeveralRepos(task.repos().size() > 1),
        Facts.Reach.defaults()
            .withScenarios(
                capabilities
                    .of(TaskAction.SCENARIOS)
                    .map(TaskCapability::offered)
                    .orElse(false)));
  }
}
