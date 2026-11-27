package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.flow.session.SessionStanding;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.stop.RefusalKind;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.Turn;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The second door into the machine: a status the task itself reports, rather than one an action led
 * to. Every report comes through here, so the machine has no entrance without a rule on it — a task
 * cannot talk itself onto a shared branch, out of one, or closed.
 */
@RequiredArgsConstructor
public class FlowReports {

  private final ReducingStore tasks;
  private final Clock clock;
  private final Checkers checkers;
  private final FlowRules rules;

  /** The status a report found the task in, and the one it left it in. */
  public record Landed(TaskStatus previous, TaskStatus now) {}

  /**
   * Whether a report of an open request, none of whose links the repository's remote owns, ships
   * nothing.
   */
  public static boolean missedShip(TaskStatus said, boolean everyLinkForeign) {
    return everyLinkForeign && said == TaskStatus.CI_POLLING;
  }

  /** The status such a report lands on: a missed ship leaves the task where it stands. */
  public static TaskStatus landing(TaskStatus said, TaskStatus standing, boolean everyLinkForeign) {
    return missedShip(said, everyLinkForeign) ? standing : said;
  }

  public TaskStatus verified(boolean passed) {
    return rules.verified(passed);
  }

  public boolean reportable(TaskStatus from, TaskStatus to) {
    return rules.reportable(from, to);
  }

  public boolean report(String taskId, TaskStatus status, @Nullable String message) {
    return report(taskId, status, AgentReport.PLAIN, message);
  }

  public boolean report(
      String taskId, TaskStatus status, AgentReport means, @Nullable String message) {
    return report(taskId, status, means, message, (was, next) -> next).isPresent();
  }

  /**
   * The same, plus whatever else the report established. Applied in the SAME write, because a
   * status that refuses to exist without its link must not be able to land first; {@code
   * alsoRecord} is handed the status BEFORE it.
   */
  public Optional<Landed> report(
      String taskId,
      TaskStatus status,
      AgentReport means,
      @Nullable String message,
      BiFunction<TaskStatus, TaskState, TaskState> alsoRecord) {
    return report(taskId, status, means, message, alsoRecord, task -> false);
  }

  /**
   * The same, told per task whether a hand-back still owes jagt a verification run. Asked of the
   * state being written, so two reports arriving together cannot disagree about it.
   */
  public Optional<Landed> report(
      String taskId,
      TaskStatus status,
      AgentReport means,
      @Nullable String message,
      BiFunction<TaskStatus, TaskState, TaskState> alsoRecord,
      Predicate<TaskState> verificationOwed) {
    return report(taskId, status, means, message, alsoRecord, verificationOwed, task -> true);
  }

  private Optional<Landed> report(
      String taskId,
      TaskStatus status,
      AgentReport means,
      @Nullable String message,
      BiFunction<TaskStatus, TaskState, TaskState> alsoRecord,
      Predicate<TaskState> verificationOwed,
      Predicate<TaskState> standing) {
    // Judged against the state being WRITTEN, not one read a moment earlier: two reports arriving
    // together
    // must not both pass on a status neither of them ends up leaving from.
    AtomicReference<Landed> landed = new AtomicReference<>();
    AtomicReference<String> refused = new AtomicReference<>();
    tasks.reduce(
        taskId,
        (reducer, task) -> {
          if (!standing.test(task)) {
            return task;
          }
          Optional<String> why =
              rules
                  .refusedReport(task.standing().status(), status, means, task.gate().planGate())
                  .or(() -> deployUndecided(task, status));
          if (why.isPresent()) {
            refused.set(why.get());
            return task.refused(RefusalKind.STATUS_REPORT);
          }
          TaskMove.Reported move =
              new TaskMove.Reported(status, !means.asks() && verificationOwed.test(task));
          TaskState said =
              reducer.reduce(
                  reducer.said(task, means),
                  move,
                  lineOf(task, means, message),
                  false,
                  clock.millis());
          said = declined(reducer, said, means, status);
          landed.set(new Landed(task.standing().status(), said.standing().status()));
          return alsoRecord.apply(task.standing().status(), said);
        });
    Optional<String> why =
        Optional.ofNullable(refused.get())
            .or(() ->
                FlowRules.reportable(status)
                    ? Optional.empty()
                    : rules.refusedReport(status, status));
    if (why.isPresent()) {
      throw Refusal.byState(why.get());
    }
    return Optional.ofNullable(landed.get());
  }

  /**
   * Why a task live on the deploy branch may not leave it yet: its deploy is undecided until the
   * pipeline on each landed tip reads green and no checker is reading it. A red one comes back as
   * the deploy conflict.
   */
  private Optional<String> deployUndecided(TaskState task, TaskStatus to) {
    Deployment deployment = task.work().deployment();
    if (!FlowRules.checkedWhereItLanded(task.standing().status())
        || to == task.standing().status()
        || deployment == null) {
      return Optional.empty();
    }
    if (deployment.undecided()) {
      return Optional.of(
          "the deploy is undecided: its pipeline has not read green yet; a red one hands you the"
              + " deploy worktree with the failing job. End your turn.");
    }
    return checkers.of(task).containsValue(CheckerPhase.READING)
        ? Optional.of(
            "the deploy is undecided: a checker is still reading it; a row it finds not met comes"
                + " back to you as a round. End your turn.")
        : Optional.empty();
  }

  /** Refuses a no_plan report where jagt holds the gate; the task is written nothing else. */
  public void requireNoPlanAllowed(String taskId) {
    AtomicReference<String> refused = new AtomicReference<>();
    tasks.reduce(
        taskId,
        (reducer, task) -> {
          Optional<String> why = reducer.refusedNoPlan(task);
          why.ifPresent(refused::set);
          return why.isPresent() ? task.refused(RefusalKind.STATUS_REPORT) : task;
        });
    if (refused.get() != null) {
      throw Refusal.byState(refused.get());
    }
  }

  /**
   * A question jagt itself puts about a task, which keeps its status: a status no session reports
   * cannot carry it.
   */
  public boolean ask(String taskId, AgentReport kind, String message) {
    AtomicBoolean found = new AtomicBoolean();
    tasks.reduce(
        taskId,
        (reducer, task) -> {
          found.set(true);
          return reducer.asked(task, kind, message, clock.millis());
        });
    return found.get();
  }

  /**
   * What a report made while standing found: it landed, the task moved on, or the task is not held.
   */
  public enum Claimed {
    LANDED,
    MOVED_ON,
    GONE
  }

  /**
   * A report that lands only while the task still has the status and turn {@code read} had, judged
   * in the write itself, so nothing lands between the check and the status.
   */
  public Claimed reportWhileStanding(
      String taskId,
      TaskState read,
      TaskStatus status,
      AgentReport means,
      @Nullable String message,
      UnaryOperator<TaskState> alsoRecord) {
    AtomicReference<Boolean> moved = new AtomicReference<>(false);
    Predicate<TaskState> standing =
        task -> {
          boolean stands = standing(task, read);
          moved.set(!stands);
          return stands;
        };
    Optional<Landed> landed =
        report(
            taskId,
            status,
            means,
            message,
            (was, next) -> alsoRecord.apply(next),
            task -> false,
            standing);
    return landed.isPresent() ? Claimed.LANDED : moved.get() ? Claimed.MOVED_ON : Claimed.GONE;
  }

  /** The same claim for a write that moves no status. */
  public Claimed claimWhileStanding(String taskId, TaskState read, UnaryOperator<TaskState> write) {
    AtomicReference<Claimed> claimed = new AtomicReference<>(Claimed.GONE);
    tasks.updateTask(
        taskId,
        task -> {
          boolean stands = standing(task, read);
          claimed.set(stands ? Claimed.LANDED : Claimed.MOVED_ON);
          return stands ? write.apply(task) : task;
        });
    return claimed.get();
  }

  /**
   * Puts the task back as {@code read} had it and hands its turn to the human, while the turn is
   * still the one the claim opened; false where the task moved on since, which is left as it is.
   */
  public boolean undoClaim(String taskId, TaskState read, Turn claimed) {
    AtomicBoolean undone = new AtomicBoolean();
    tasks.reduce(
        taskId,
        (reducer, task) -> {
          if (!task.work().turnOrNone().sameTurnAs(claimed)) {
            return task;
          }
          undone.set(true);
          long now = clock.millis();
          return reducer.turnClosed(task.restoredFrom(read, now), now);
        });
    return undone.get();
  }

  private static boolean standing(TaskState task, TaskState read) {
    return SessionStanding.of(task).equals(SessionStanding.of(read));
  }

  private TaskState declined(
      TaskReducer reducer, TaskState said, AgentReport means, TaskStatus status) {
    TaskState declined = said;
    for (Map.Entry<String, CheckerPhase> checker : checkers.of(said).entrySet()) {
      if (checker.getValue() == CheckerPhase.ROUND_OPEN) {
        declined =
            reducer.checkerDeclined(declined, checker.getKey(), means, status, clock.millis());
      }
    }
    return declined;
  }

  /**
   * A question still reaches the card; once one is behind, the line falls back to the status's own.
   */
  private static @Nullable String lineOf(
      TaskState task, AgentReport means, @Nullable String message) {
    if (!FlowRules.holdsItsLine(task.standing().status()) || means.asks()) {
      return message;
    }
    return task.standing().report().asks() ? null : task.standing().message();
  }

  /**
   * What jagt read off the task's review round on the host, the one way into a verdict. A read that
   * moves nothing writes nothing: a polled round reads the same every interval.
   */
  public Optional<Landed> read(String taskId, TaskStatus concluded, String message) {
    AtomicReference<Landed> landed = new AtomicReference<>();
    tasks.reduce(
        taskId,
        (reducer, task) -> {
          TaskStatus was = task.standing().status();
          Optional<TaskStatus> lands = rules.readLands(was, concluded);
          if (lands.isEmpty()) {
            return task;
          }
          landed.set(new Landed(was, lands.get()));
          if (lands.get() == was) {
            return task;
          }
          return reducer.reduce(
              task,
              new TaskMove.Read(concluded),
              FlowRules.standsThroughRead(task.standing().report())
                  ? task.standing().message()
                  : message,
              false,
              clock.millis());
        });
    return Optional.ofNullable(landed.get());
  }

  /**
   * What jagt read of the pipeline on a deployed task's commit: the line the task carries while it
   * is open, or the deploy handed back for a red the code owns. A task that is not live on the
   * deploy branch is left alone, and so is one already saying it; false where nothing was written.
   * {@code redProject} is the repository whose pipeline is red on the code, null while none is; the
   * handed-back deploy records it as conflicted for that cause.
   */
  public boolean readDeploy(
      String taskId, Generation readAt, @Nullable String redProject, String message) {
    AtomicBoolean written = new AtomicBoolean();
    tasks.reduce(
        taskId,
        (reducer, task) ->
            reducer.observed(
                task,
                readAt,
                current -> deployRead(reducer, current, redProject, message, written)));
    return written.get();
  }

  private TaskState deployRead(
      TaskReducer reducer,
      TaskState task,
      @Nullable String redProject,
      String message,
      AtomicBoolean written) {
    boolean redOnTheCode = redProject != null;
    if (!FlowRules.checkedWhereItLanded(task.standing().status())) {
      return task;
    }
    TaskStatus was = task.standing().status();
    boolean moves = rules.deployReadLands(was, redOnTheCode).filter(to -> to != was).isPresent();
    if (!moves && message.equals(task.standing().message())) {
      return task;
    }
    written.set(true);
    TaskState read =
        reducer.reduce(task, new TaskMove.DeployRead(redOnTheCode), message, moves, clock.millis());
    return redProject == null
        ? read
        : reducer.deployStepped(
            read,
            new DeployStep.Conflicted(redProject, Deployment.Cause.RED_PIPELINE),
            clock.millis());
  }

  /**
   * A deploy conflict held by red pipelines alone, ended because a later pipeline on the deploy
   * branch containing the deploy commit is green: the task is live on the deploy branch again. Any
   * other task is left alone.
   */
  public void endRedConflict(String taskId, Generation readAt, String message) {
    tasks.reduce(
        taskId,
        (reducer, task) ->
            reducer.observed(
                task,
                readAt,
                current -> {
                  if (!FlowRules.deployConflicted(current.standing().status())
                      || current.work().deployment() == null
                      || !current.work().deployment().heldByRedPipeline()) {
                    return current;
                  }
                  return reducer.reduce(
                      current, new TaskMove.DeployRead(false), message, true, clock.millis());
                }));
  }

  /**
   * Marks {@code bare} as the landed targets that settled with no pipeline listed; a target listed
   * since is cleared.
   */
  public void deployBare(String taskId, Generation readAt, List<String> bare) {
    tasks.reduce(
        taskId,
        (reducer, task) ->
            reducer.observed(
                task,
                readAt,
                current ->
                    reducer.deployStepped(current, new DeployStep.Bare(bare), clock.millis())));
  }

  /**
   * Counts a read that could not list {@code unread}'s pipeline, null where it listed every one;
   * the reads running, at most.
   */
  public int deployReadOn(String taskId, Generation readAt, @Nullable String unread) {
    tasks.reduce(
        taskId,
        (reducer, task) ->
            reducer.observed(
                task,
                readAt,
                current ->
                    reducer.deployStepped(current, new DeployStep.ReadOn(unread), clock.millis())));
    return tasks
        .task(taskId)
        .map(taskState -> taskState.work().deployment())
        .map(deployment ->
            deployment.targets().stream().mapToInt(Deployment.Target::unreadReads).max().orElse(0))
        .orElse(0);
  }

  /**
   * The pipeline of {@code project}'s tip re-run, recorded before it is sent so a restart never
   * sends it again.
   */
  public void deployRetried(String taskId, String project, String tip) {
    tasks.reduce(
        taskId,
        (reducer, task) ->
            reducer.deployStepped(task, new DeployStep.Retried(project, tip), clock.millis()));
  }

  public void deployGreen(String taskId, Generation readAt) {
    tasks.reduce(
        taskId,
        (reducer, task) ->
            reducer.observed(
                task,
                readAt,
                current -> reducer.deployStepped(current, new DeployStep.Green(), clock.millis())));
  }

  public void deployAsked(String taskId, String digest) {
    tasks.reduce(
        taskId,
        (reducer, task) ->
            reducer.deployStepped(task, new DeployStep.Asked(digest), clock.millis()));
  }
}
