package dev.jagt.orchestrator.job.session;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.SessionStanding;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.Step;
import dev.jagt.orchestrator.task.turn.Turn;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * A session whose turn ended with no report and whose process runs is jagt's to wake: one line to
 * it, then one fresh session, each given the watchdog's threshold to answer; only then the task
 * stops for the human.
 */
@Service
@RequiredArgsConstructor
public class SilentWake {

  static final String NUDGE = "your turn ended with no report.";
  static final String RELAY = "a fresh session took over: your turn ended with no report.";
  static final String EVIDENCE =
      "its turn ended with no report and nothing has moved since, process running; one nudge and"
          + " one fresh session brought nothing";

  private final AgentSessions sessions;
  private final StepRelay relay;
  private final TaskStops stops;
  private final OrchestratorProperties properties;
  private final Clock clock;

  public void wake(String taskId, TaskState task) {
    long since = SessionPhase.wakeableSince(task);
    if (since == 0 || task.work().stop() != null || sessions.composing(taskId)) {
      return;
    }
    Turn turn = task.work().turnOrNone();
    boolean answerDue =
        clock.millis() - turn.since() >= properties.watchdog().staleAfter().toMillis();
    SessionStanding read = SessionStanding.of(task);
    if (turn.holds(Step.SILENT.id(), Turn.digestOf(RELAY))) {
      if (answerDue) {
        stops.stop(taskId, TaskStop.Trigger.SILENT, EVIDENCE);
      }
    } else if (turn.holds(Step.SILENT.id(), Turn.digestOf(NUDGE)) && turn.since() >= since) {
      if (answerDue) {
        relay.relay(taskId, Step.SILENT, RELAY, StepRelay.Delivery.UNDER_FRESH, read);
      }
    } else {
      relay.say(taskId, Step.SILENT, NUDGE, read);
    }
  }
}
