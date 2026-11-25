package dev.jagt.orchestrator.job.session;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.presence.SessionProbe;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.text.Blank;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reads every open task's session each tick and alerts for the ones that have stopped in a status
 * where the agent should be working. What it finds is the session record's, written by the reducer
 * alone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WatchdogService implements Job {

  private final StateService stateService;
  private final OrchestratorProperties properties;
  private final SessionProbe probe;
  private final Clock clock;

  @Override
  public String id() {
    return "watchdog";
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public String describe() {
    return "alert when an agent has gone quiet in a status where it is supposed to be working";
  }

  @Override
  public Duration every() {
    return probe.every();
  }

  /**
   * The statuses where the agent is EXPECTED to be doing something, so silence means death — NEW
   * included, for an agent that died before its first status update. Every other status idles BY
   * DESIGN and stays unwatched.
   */
  static boolean watches(TaskStatus status) {
    return FlowRules.watched(status);
  }

  @Override
  public void run() {
    long now = clock.millis();
    Map<String, TaskState> tasks = stateService.tasks();
    tasks.forEach((taskId, task) -> verdict(taskId, task, now));
  }

  /**
   * One task on its own, for a session that just reported itself and must not wait out the
   * interval.
   */
  public void check(String taskId) {
    stateService.task(taskId).ifPresent(task -> verdict(taskId, task, clock.millis()));
  }

  /** An agent that exited without a word from its harness, told by what it printed last. */
  public void ended(String taskId, String printed) {
    long now = clock.millis();
    String line = Blank.toNull(printed);
    SessionSign.Ended sign = new SessionSign.Ended(line == null ? null : OneLine.clipped(line));
    SessionSign.Read unread =
        new SessionSign.Read(
            Session.Window.State.UNREAD,
            0,
            0,
            0,
            properties.watchdog().staleAfter().toMillis(),
            null);
    stateService.reduce(
        taskId, (reducer, task) -> reducer.signed(reducer.signed(task, unread, now), sign, now));
    check(taskId);
  }

  private void verdict(String taskId, TaskState task, long now) {
    if (FlowRules.closed(task.standing().status())) {
      return;
    }
    long staleMs = properties.watchdog().staleAfter().toMillis();
    SessionSign.Read read;
    try {
      read = probe.read(taskId, task, staleMs, now);
    } catch (SessionHost.WindowsUnreadable unknown) {
      return;
    }
    stateService.reduce(
        taskId,
        (reducer, current) ->
            startedOverSince(task, current) ? current : reducer.signed(current, read, now));
    alert(taskId, stateService.task(taskId).orElse(task), now);
  }

  private static boolean startedOverSince(TaskState looked, TaskState current) {
    return current.work().session().generation() != looked.work().session().generation();
  }

  private void alert(String taskId, TaskState task, long now) {
    if (!watches(task.standing().status()) || !SessionPhase.silent(task)) {
      forgetAlert(taskId, task);
      return;
    }
    long since = SessionPhase.silentSince(task);
    if (SessionPhase.silentSinceUnasked(task) == 0
        || task.round().toldOrNone().said(ToldOnce.Kind.SILENCE, String.valueOf(since))) {
      return;
    }
    String said = Objects.requireNonNullElse(SessionPhase.silentBecause(task), "no sign of life");
    stateService.reduce(
        taskId,
        (reducer, current) -> reducer.told(current, ToldOnce.Kind.SILENCE, String.valueOf(since)));
    log.atWarn()
        .setMessage("task stopped")
        .addKeyValue("task", taskId)
        .addKeyValue("said", OneLine.clipped(said))
        .addKeyValue("cause", cause(task.work().session()))
        .addKeyValue("silent", (now - since) / 60_000)
        .log();
  }

  private void forgetAlert(String taskId, TaskState task) {
    if (task.round().toldOrNone().has(ToldOnce.Kind.SILENCE)) {
      stateService.reduce(
          taskId, (reducer, current) -> reducer.untold(current, ToldOnce.Kind.SILENCE));
    }
  }

  private static String cause(Session session) {
    Session.Halt halt = session.halt();
    if (halt == null) {
      return "silent";
    }
    return halt.cause() != null ? halt.cause() : halt.kind().name().toLowerCase(Locale.ROOT);
  }
}
