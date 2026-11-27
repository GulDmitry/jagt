package dev.jagt.orchestrator.service.presence;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Generation;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Reads the task's window into its session record, and answers from the record. */
@Service
@RequiredArgsConstructor
public class SessionPresence implements AgentPresence {

  private final StateService stateService;
  private final SessionProbe probe;
  private final OrchestratorProperties properties;
  private final Clock clock;

  @Override
  public boolean agentLive(String taskId) {
    TaskState task = stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
    long now = clock.millis();
    SessionSign.Read read;
    try {
      read = probe.read(taskId, task, properties.watchdog().staleAfter().toMillis(), now);
    } catch (SessionHost.WindowsUnreadable unread) {
      throw new Unread(String.valueOf(unread.getMessage()));
    }
    Generation started = Generation.of(Generation.Kind.SESSION_START, task);
    stateService.reduce(taskId, (reducer, current) -> reducer.signed(current, started, read, now));
    Boolean alive =
        stateService.task(taskId).map(current -> current.work().session().alive()).orElse(null);
    if (alive == null) {
      throw new Unread("the window was not read");
    }
    return alive;
  }
}
