package dev.jagt.orchestrator.service.session;

import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.presence.SessionLedger;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Ends every live session as gone and closes its windows; the next start finds them missing. */
@Component
@ConditionalOnProperty(name = "orchestrator.on-stop", havingValue = "end")
@RequiredArgsConstructor
@Slf4j
public class ShutdownEndings implements SmartLifecycle {

  /** Stops after the web server's stop phase (default phase minus 2048). */
  static final int PHASE = DEFAULT_PHASE - 3072;

  private static final String REASON = "jagt stopped";

  private final StateService state;
  private final SessionLedger ledger;
  private final AgentSessions sessions;
  private boolean running;

  @Override
  public void start() {
    running = true;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    return PHASE;
  }

  @Override
  public void stop() {
    running = false;
    for (Map.Entry<String, TaskState> entry : state.tasks().entrySet()) {
      if (live(entry.getValue())) {
        endSession(entry.getKey());
      }
    }
  }

  private static boolean live(TaskState task) {
    return SessionPhase.of(task.session()).live();
  }

  private void endSession(String taskId) {
    ledger.sign(taskId, new SessionSign.Ended(REASON));
    try {
      sessions.killWindows(taskId);
    } catch (RuntimeException unread) {
      log.atWarn()
          .setMessage("session windows left")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", unread.toString())
          .addKeyValue("effect", "the next start finds the window and reads it")
          .log();
    }
  }
}
