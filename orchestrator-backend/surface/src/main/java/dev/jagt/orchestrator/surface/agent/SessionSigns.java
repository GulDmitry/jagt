package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.flow.Generation;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.job.session.WatchdogService;
import dev.jagt.orchestrator.service.presence.SessionLedger;
import dev.jagt.orchestrator.service.usage.AgentSpendReader;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** A session's sign of life, the verdict on it, and what it has spent. */
@Service
@RequiredArgsConstructor
public class SessionSigns {

  private final SessionLedger ledger;
  private final WatchdogService watchdog;
  private final AgentSpendReader agentSpend;

  public void asked(String taskId, Generation readAt, SessionSign.Asked sign) {
    ledger.sign(taskId, readAt, sign);
    watchdog.check(taskId);
  }

  /**
   * {@code readAt} is the session start the sign was sent from; {@code sessionLog} is the file the
   * session appends to, or null where none was named.
   */
  public void record(
      String taskId, Generation readAt, @Nullable Path sessionLog, SessionSign sign) {
    ledger.record(taskId, readAt, sessionLog, sign);
    watchdog.check(taskId);
    // Off this thread: a read of the log, or the state lock a sweep holds, must not delay the
    // answer.
    if (sessionLog != null) {
      Thread.startVirtualThread(() -> agentSpend.charge(taskId, sessionLog));
    }
  }
}
