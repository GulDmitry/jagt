package dev.jagt.orchestrator.service.presence;

import dev.jagt.orchestrator.flow.Generation;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Map;

/** A sign of life that arrives with every call is written at most once per throttle window. */
public final class KeepAlive {

  static final long THROTTLE_MS = 15_000;

  private KeepAlive() {}

  public static void beat(
      StateService stateService, Map.Entry<String, TaskState> task, SessionSign sign, long now) {
    if (now - task.getValue().standing().lastActiveTimestamp() > THROTTLE_MS) {
      Generation started = Generation.of(Generation.Kind.SESSION_START, task.getValue());
      stateService.reduce(
          task.getKey(),
          (reducer, held) ->
              reducer.observed(
                  held, started, current -> reducer.signed(current.touched(now), sign, now)));
    }
  }
}
