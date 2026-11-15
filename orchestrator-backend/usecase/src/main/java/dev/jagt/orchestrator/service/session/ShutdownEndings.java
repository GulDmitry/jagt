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
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Ends every live session as gone and closes its windows; the next start finds them missing. */
@Component
@ConditionalOnProperty(name = "orchestrator.on-stop", havingValue = "end")
@RequiredArgsConstructor
@Slf4j
public class ShutdownEndings {

  private static final String REASON = "jagt stopped";

  private final StateService state;
  private final SessionLedger ledger;
  private final AgentSessions sessions;

  @EventListener(ContextClosedEvent.class)
  public void end() {
    for (Map.Entry<String, TaskState> entry : state.tasks().entrySet()) {
      if (live(entry.getValue())) {
        endSession(entry.getKey());
      }
    }
  }

  private static boolean live(TaskState task) {
    return switch (SessionPhase.of(task.session())) {
      case WORKING, IDLE, QUIET -> true;
      case NONE, FAILED, GONE -> false;
    };
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
