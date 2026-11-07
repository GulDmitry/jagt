package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.flow.TurnKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskState;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Once per start, a running session holding an open turn and writing while jagt was down repeats
 * its last report, which may be lost.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestartAskJob implements TaskJob {

  static final String FACTS = "jagt was restarted.";

  private final AgentSessions sessions;
  private final AgentPresence presence;
  private final StepRelay relay;
  private final AliveStamp alive;

  @Override
  public String id() {
    return "restart-ask";
  }

  @Override
  public String describe() {
    return "ask each warm running session holding an open turn and writing while jagt was down to"
               + " repeat its last report, once after a start";
  }

  @Override
  public @Nullable Duration every() {
    return null;
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public Watch watch() {
    return Watch.defaults().withTurns(TurnKind.OPEN);
  }

  @Override
  public void run(String taskId, TaskState task) {
    ask(taskId, Standing.of(task));
  }

  private boolean running(String taskId) {
    try {
      return presence.agentLive(taskId);
    } catch (AgentPresence.Unread unread) {
      return false;
    }
  }

  private void ask(String taskId, Standing read) {
    if (!running(taskId) || sessions.composing(taskId)) {
      log.atInfo()
          .setMessage("restart ask skipped")
          .addKeyValue("task", taskId)
          .addKeyValue(
              "cause",
              "no session is running, its window was not read, or the human is typing in it")
          .addKeyValue("effect", "nothing said, the session is not revived for it")
          .log();
      return;
    }
    if (sessions.cold(taskId)) {
      log.atInfo()
          .setMessage("restart ask skipped")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", "the session is idle past its cache lifetime")
          .addKeyValue("effect", "nothing said, its report predates anything the restart lost")
          .log();
      return;
    }
    long down = alive.lastAliveBeforeStart();
    if (down == 0 || !sessions.loggedSince(taskId, down)) {
      log.atInfo()
          .setMessage("restart ask skipped")
          .addKeyValue("task", taskId)
          .addKeyValue(
              "cause", "its log was not written while jagt was down, or jagt left no alive stamp")
          .addKeyValue("effect", "nothing said, no report of it was lost")
          .log();
      return;
    }
    try {
      relay.say(taskId, Step.RESTARTED, FACTS, read);
    } catch (RuntimeException e) {
      log.atWarn()
          .setMessage("restart ask not sent")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "the session was not asked")
          .log();
    }
  }
}
