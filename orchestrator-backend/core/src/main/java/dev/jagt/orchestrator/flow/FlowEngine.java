package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.capability.Capabilities;
import dev.jagt.orchestrator.flow.capability.CapabilityInterceptor;
import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
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

  private final ReducingStore tasks;
  private final Capabilities capabilities;
  private final FactSources sources;
  private final Clock clock;
  private final FlowRules rules;

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
    return runWith(taskIdOrAlias, action, project, null);
  }

  /** As above, on the line a verb that takes one types; never a project. */
  public String runLine(String taskIdOrAlias, TaskAction action, String line) {
    return runWith(taskIdOrAlias, action, null, line);
  }

  private String runWith(
      String taskIdOrAlias, TaskAction action, @Nullable String project, @Nullable String line) {
    String taskId = tasks.canonicalTaskId(taskIdOrAlias);
    TaskState task = tasks.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskIdOrAlias));
    Facts read = facts(task, taskId);
    Facts facts =
        project == null
            ? read
            : capabilities.of(action).map(named -> named.naming(read)).orElse(read);
    if (!rules.allows(task.status(), action, facts)) {
      throw notAvailable(taskId, task, action);
    }
    Facts pressed = pressed(facts, taskId);
    ActionOrigin by = sources.pressedBy();
    if (!rules.allows(task.status(), action, pressed, by)) {
      Decline decline =
          rules.declined(action, pressed, by).orElseThrow(() -> notAvailable(taskId, task, action));
      if (decline.quiet()) {
        return apply(
            taskId,
            task.status(),
            action,
            Outcome.nothing(action.id() + " " + taskId + ": " + decline.why()));
      }
      throw Refusal.byState(action.id() + " " + taskId + " refused: " + decline.why());
    }
    TaskCapability capability =
        capabilities
            .of(action)
            .orElseThrow(() ->
                new IllegalStateException("No capability declared for `" + action.id() + "`"));
    if (project != null) {
      named(taskId, task, action, project, capability);
    }
    if (line != null) {
      if (!action.takesLine()) {
        throw Refusal.byState(action.id() + " takes no line");
      }
      capability.vetLine(line);
    }
    return apply(taskId, task.status(), action, wrapped(taskId, action, capability, project, line));
  }

  private Refusal notAvailable(String taskId, TaskState task, TaskAction action) {
    Facts projected = Facts.projected(task);
    return new Refusal(
        Refusal.Code.ACTION_NOT_AVAILABLE,
        action.label()
            + " is not available for "
            + taskId
            + " (it is "
            + task.status()
            + " — "
            + Move.forTask(
                    rules,
                    task.status(),
                    projected.withDelivery(
                        projected
                            .delivery()
                            .withShippedWithoutRequest(sources.shippedWithoutRequest(task))),
                    dev.jagt.orchestrator.flow.view.RoundState.of(task.report(), false),
                    SessionPhase.silent(task))
                .hint()
            + ")");
  }

  /** The facts a press alone reads, each read at most once and only by the gate that asks. */
  private Facts pressed(Facts read, String taskId) {
    return read.withDelivery(read.delivery().withMergedNow(sources.mergedNow(taskId)))
        .withWorkspace(read.workspace().withDistrust(sources.distrust(taskId)));
  }

  private void named(
      String taskId, TaskState task, TaskAction action, String project, TaskCapability capability) {
    if (!action.takesProject()) {
      throw Refusal.byState(action.id() + " takes no project");
    }
    if (rules.next(task.status(), action, Outcome.Kind.RELAYED).isEmpty()) {
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
      String taskId,
      TaskAction action,
      TaskCapability capability,
      @Nullable String project,
      @Nullable String line) {
    Supplier<Outcome> work =
        line != null
            ? () -> capability.runLine(taskId, line)
            : project == null
                ? () -> capability.run(taskId)
                : () -> capability.run(taskId, project);
    for (CapabilityInterceptor interceptor : capabilities.around(action).reversed()) {
      Supplier<Outcome> inner = work;
      work = () -> interceptor.around(taskId, inner);
    }
    return work.get();
  }

  private String apply(String taskId, TaskStatus was, TaskAction action, Outcome outcome) {
    Optional<TaskStatus> next = rules.next(was, action, outcome.kind());
    if (next.isPresent() || outcome.stamp() != null) {
      // Recorded even when the status is unchanged: a second round onto the same request happened.
      tasks.reduce(taskId, (reducer, task) -> landed(reducer, task, was, next, action, outcome));
    }
    if (outcome.kind().refuses()) {
      throw new IllegalStateException(outcome.message(), outcome.cause());
    }
    return outcome.message();
  }

  private TaskState landed(
      TaskReducer reducer,
      TaskState task,
      TaskStatus was,
      Optional<TaskStatus> next,
      TaskAction action,
      Outcome outcome) {
    // A report that landed while the work ran is newer than the work's outcome.
    if (next.isPresent() && task.status() != was) {
      return task;
    }
    String line = outcome.stamp() == null ? task.message() : outcome.stamp();
    long at = clock.millis();
    TaskState written =
        next.isPresent()
            ? reducer.reduce(task, new TaskMove.Ran(action, outcome.kind()), line, true, at)
            : reducer.stayed(task, line, true, at);
    return outcome.stamp() == null ? written : reducer.stamped(written, outcome.stampMeans());
  }

  /** The real liveness probe, unlike the projection's: this one is about to act on the answer. */
  private Facts facts(TaskState task, String taskId) {
    return new Facts(
        Facts.Delivery.defaults()
            .withHasReviewRequest(task.hasReviewRequest())
            .withLiveDeploy(Facts.deployLive(task))
            .withRequestMerged(task.requestsMerged())
            .withRevertConflict(Facts.revertConflicted(task))
            .withShippedWithoutRequest(sources.shippedWithoutRequest(task)),
        Facts.Hold.defaults()
            .withAgentLive(() -> sources.agentLive(taskId))
            .withStopped(task.stopped())
            .withTierAbove(Facts.tierAbove(task)),
        Facts.Workspace.defaults()
            .withGitStateChanged(task.gitStateChanged())
            .withRepoRequested(task.asksForRepository())
            .withSeveralRepos(task.repos().size() > 1),
        Facts.Reach.defaults()
            .withScenarios(
                capabilities.of(TaskAction.SCENARIOS).map(TaskCapability::offered).orElse(false))
            .withShellEscape(
                capabilities.of(TaskAction.RUN).map(TaskCapability::offered).orElse(false)));
  }
}
