package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.session.SessionSign;
import java.nio.file.Path;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Writes what a session's harness reported as a sign on the task. */
@Component
@RequiredArgsConstructor
public class SessionLedger {

  private final StateService stateService;
  private final Clock clock;

  public void record(String taskId, @Nullable Path sessionLog, SessionSign sign) {
    if (sessionLog != null) {
      sign(taskId, new SessionSign.LogNamed(sessionLog.toString()));
    }
    sign(taskId, sign);
  }

  public void sign(String taskId, SessionSign sign) {
    long now = clock.millis();
    stateService.reduce(taskId, (reducer, task) -> reducer.signed(task, sign, now));
  }
}
