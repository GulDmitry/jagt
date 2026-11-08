package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Facts;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.StopHold;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.LoopBreaker;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.RoundTrail;
import dev.jagt.orchestrator.task.StopTriage;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * The usecase door to a task's stop: tripped by the loop breaker or a miss, announced once, cleared
 * by a lift or by the read its trigger names.
 */
@Service
@RequiredArgsConstructor
public class TaskStops {

  public enum Relay {
    RELAY,
    UNCHANGED,
    ASKING,
    STOPPED
  }

  /**
   * The refusals of a read that show the checks a stop waited on are gone; any other refusal says
   * nothing about them.
   */
  private static final Set<ReviewSweepService.SweepResult.Kind> DISPROVING_REFUSALS =
      EnumSet.of(
          ReviewSweepService.SweepResult.Kind.GONE, ReviewSweepService.SweepResult.Kind.NO_MR);

  private final PhaseProfiles profiles;
  private final StateService stateService;
  private final LoopBreaker.Current breakers;
  private final StopAnnouncements announcements;
  private final Clock clock;

  /** The stop standing once this one was set: an earlier stop stands, and keeps its own words. */
  public Optional<TaskStop> stop(String taskId, TaskStop.Trigger trigger, String evidence) {
    return trip(
        taskId, task -> task, task -> Optional.of(new TaskStop(trigger, clock.millis(), evidence)));
  }

  /** A pause the human ordered in words, held to the same offer as their press. */
  public String pauseOnWord(String taskIdOrAlias, String reason) {
    String taskId = stateService.canonicalTaskId(taskIdOrAlias);
    TaskState task = stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskIdOrAlias));
    if (!FlowRules.allows(task.status(), TaskAction.PAUSE, Facts.projected(task))) {
      throw new Refusal(
          Refusal.Code.ACTION_NOT_AVAILABLE,
          "pause is not available for " + taskId + " (it is " + task.status() + ")");
    }
    stop(taskId, TaskStop.Trigger.PAUSE, reason);
    return "pause " + taskId + ": nothing of jagt moves it until the human lifts the stop";
  }

  /**
   * False where a round of {@code step}, a move too, would run past a ceiling, which stops the
   * task, or a stop stands.
   */
  public boolean roundDue(String taskId, String step) {
    LoopBreaker breaker = breakers.current();
    return trip(
            taskId,
            task -> task,
            task ->
                breaker
                    .beforeRound(step, task.turnOrNone(), task.trailOrNone(), clock.millis())
                    .or(
                        () ->
                            breaker.beforeMove(task.history(), task.trailOrNone(), clock.millis())))
        .isEmpty();
  }

  /**
   * False where one more unattended move would run past the hour's ceiling, which stops the task,
   * or a stop stands.
   */
  public boolean moveDue(String taskId) {
    LoopBreaker breaker = breakers.current();
    return trip(
            taskId,
            task -> task,
            task -> breaker.beforeMove(task.history(), task.trailOrNone(), clock.millis()))
        .isEmpty();
  }

  /**
   * A round identical to the last one relayed, bringing no new note on green checks, or met by a
   * standing question, is not news.
   */
  public Relay round(String taskId, ReviewFacts facts, String brief, String tip) {
    String notes = facts.noteFingerprint();
    String failing = failing(facts);
    String key = factKey(facts, failing, notes, tip);
    String digest = Turn.digestOf(key.isEmpty() ? brief : key);
    AtomicReference<Relay> verdict = new AtomicReference<>(Relay.RELAY);
    LoopBreaker breaker = breakers.current();
    Optional<TaskStop> stop =
        trip(
            taskId,
            task -> {
              verdict.set(verdictOn(task, digest, notes, failing));
              return verdict.get() != Relay.RELAY
                  ? task
                  : task.withTrail(
                      task.trailOrNone()
                          .withRound(
                              new RoundTrail.Round(
                                  clock.millis(),
                                  facts.discussion().threads().size(),
                                  failing,
                                  digest,
                                  notes)));
            },
            task ->
                verdict.get() != Relay.RELAY
                    ? Optional.empty()
                    : breaker.afterRound(task.trailOrNone(), task.history(), clock.millis()));
    return stop.isPresent() ? Relay.STOPPED : verdict.get();
  }

  private static Relay verdictOn(TaskState task, String digest, String notes, String failing) {
    RoundTrail trail = task.trailOrNone();
    if (task.report().asks() && !trail.newNotes(notes)) {
      return Relay.ASKING;
    }
    return trail.relayed(digest) || trail.noNewNote(notes, failing) ? Relay.UNCHANGED : Relay.RELAY;
  }

  /**
   * Whether the agent's own question stands, which holds the checks back and nothing of the
   * threads.
   */
  public boolean asking(String taskId) {
    return stateService.task(taskId).filter(task -> task.report().asks()).isPresent();
  }

  /** The round's threads told against the last round relayed into the task. */
  public String sinceLast(String taskId, ReviewFacts facts) {
    return stateService.task(taskId).map(task -> task.trailOrNone().sinceLast(facts)).orElse("");
  }

  public boolean mayShip(String taskId) {
    LoopBreaker breaker = breakers.current();
    return trip(
            taskId,
            task -> task,
            task -> breaker.beforeShip(task.trailOrNone(), task.history(), clock.millis()))
        .isEmpty();
  }

  /** Writes the read onto the standing stop only while that stop is the one it was made for. */
  public void triaged(String taskId, String stopKey, StopTriage read) {
    stateService.updateTask(taskId, task -> TaskReducer.triaged(task, stopKey, read));
  }

  public boolean stopped(String taskId) {
    return stateService.task(taskId).filter(TaskState::stopped).isPresent();
  }

  /** Whose the standing stop is to lift, in the phase the task stands in. */
  private StopHold holdOf(TaskState task) {
    return StopHold.of(task, profiles.at(task));
  }

  /** Whether the Master was asked about the stop standing and may lift it, within its bound. */
  public boolean masterMayLift(String taskId) {
    return stateService
        .task(taskId)
        .filter(task -> StopHold.liftableByTheMaster(task, profiles.at(task)))
        .isPresent();
  }

  /**
   * A human's lift, or a deploy's, restores the Master's lifts; the Master's own lift spends one.
   */
  public void lift(String taskId) {
    boolean byTheMaster = CaseDoor.byTheMaster();
    stateService.updateTask(
        taskId,
        task ->
            TaskReducer.masterLiftedStops(
                TaskReducer.lift(task, clock.millis()),
                byTheMaster ? task.masterOrNone().liftedStops() + 1 : 0));
  }

  /** A human's lift that leaves the task's session due on the next model tier. */
  public void escalate(String taskId) {
    stateService.updateTask(
        taskId,
        task ->
            TaskReducer.masterLiftedStops(TaskReducer.escalated(task, clock.millis()), 0));
  }

  /** A deploy by a human restores the Master's lifts, whether or not a stop stands. */
  public void restoreMasterLifts(String taskId) {
    stateService.updateTask(taskId, task -> TaskReducer.masterLiftedStops(task, 0));
  }

  /**
   * The stop a read of the checks ended: one whose trigger ends on a read, and the read listed the
   * checks, not red.
   */
  public Optional<TaskStop> endOnRead(String taskId, Pipeline read) {
    return endWhen(
        taskId,
        TaskStop.Trigger.Ends.HOST_READ,
        read != Pipeline.UNKNOWN && read != Pipeline.RED,
        "");
  }

  /** The stop a read listing every deploy pipeline ended: one whose trigger ends on it. */
  public Optional<TaskStop> endOnDeployRead(String taskId) {
    return endWhen(taskId, TaskStop.Trigger.Ends.DEPLOY_READ, true, "");
  }

  /** The stop that ended because the session's window runs again: one whose trigger ends on it. */
  public Optional<TaskStop> endOnSessionAlive(String taskId) {
    return endWhen(taskId, TaskStop.Trigger.Ends.SESSION_ALIVE, true, "");
  }

  /** The stop a session's report that is no wait ended: one whose trigger ends on it. */
  public Optional<TaskStop> endOnReport(String taskId) {
    return endWhen(taskId, TaskStop.Trigger.Ends.SESSION_REPORT, true, "");
  }

  /** Whether a stop of {@code trigger} stands on the task. */
  public boolean standing(String taskId, TaskStop.Trigger trigger) {
    return stateService
        .task(taskId)
        .map(TaskState::stop)
        .filter(stop -> stop.trigger() == trigger)
        .isPresent();
  }

  /**
   * The stop a refused read ended: one whose trigger ends on a read, and the refusal says the
   * checks are no more on a request the stop was counted on; {@code request} is blank where the
   * refusal is about none.
   */
  public Optional<TaskStop> endOnRefusal(
      String taskId, ReviewSweepService.SweepResult.Kind refusal, String request) {
    return endWhen(
        taskId, TaskStop.Trigger.Ends.HOST_READ, DISPROVING_REFUSALS.contains(refusal), request);
  }

  private Optional<TaskStop> endWhen(
      String taskId, TaskStop.Trigger.Ends ends, boolean disproves, String request) {
    AtomicReference<@Nullable TaskStop> ended = new AtomicReference<>();
    stateService.updateTask(
        taskId,
        task -> {
          TaskState after =
              disproves ? TaskReducer.ended(task, ends, request, clock.millis()) : task;
          if (task.stopped() && !after.stopped()) {
            ended.set(task.stop());
          }
          return after;
        });
    return Optional.ofNullable(ended.get());
  }

  /** The stop standing once {@code check} read the task. */
  public Optional<TaskStop> trip(String taskId, Function<TaskState, Optional<TaskStop>> check) {
    return trip(taskId, task -> task, check);
  }

  /** The stop standing once {@code record} ran and {@code check} read what it recorded. */
  private Optional<TaskStop> trip(
      String taskId,
      UnaryOperator<TaskState> record,
      Function<TaskState, Optional<TaskStop>> check) {
    AtomicReference<@Nullable TaskStop> standing = new AtomicReference<>();
    AtomicReference<@Nullable TaskStop> tripped = new AtomicReference<>();
    AtomicReference<@Nullable TaskState> stoppedTask = new AtomicReference<>();
    stateService.updateTask(
        taskId,
        task -> {
          if (task.stopped()) {
            standing.set(task.stop());
            return task;
          }
          TaskState recorded = record.apply(task);
          Optional<TaskStop> stop = check.apply(recorded);
          TaskState after =
              stop.map(s -> TaskReducer.stop(recorded, s, clock.millis())).orElse(recorded);
          stop.ifPresent(tripped::set);
          stop.ifPresent(s -> stoppedTask.set(after));
          stop.ifPresent(standing::set);
          return after;
        });
    Optional.ofNullable(tripped.get())
        .ifPresent(
            stop ->
                announcements.announce(
                    taskId,
                    Objects.requireNonNull(stoppedTask.get()),
                    stop,
                    holdOf(stoppedTask.get())));
    return Optional.ofNullable(standing.get());
  }

  /**
   * Equal for one round read twice, the brief's wording aside; empty where a fact it turns on is
   * unknown.
   */
  private static String factKey(ReviewFacts facts, String failing, String notes, String tip) {
    boolean red = Pipeline.of(facts.pipeline().status()) == Pipeline.RED;
    boolean unknown =
        (red && (failing.isBlank() || tip.isBlank()))
            || (!facts.discussion().threads().isEmpty() && notes.isBlank())
            || (!red && facts.discussion().threads().isEmpty());
    return unknown ? "" : (red ? failing + "@" + tip : "") + "\n" + notes;
  }

  private static String failing(ReviewFacts facts) {
    return Pipeline.of(facts.pipeline().status()) == Pipeline.RED
        ? facts.pipeline().failure().lines().findFirst().orElse("")
        : "";
  }
}
