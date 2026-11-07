package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStop;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/** The stop region's states: none, or the way the standing stop ends. */
public enum StopKind {
  NONE,
  LIFT,
  HOST_READ,
  SESSION_ALIVE,
  SESSION_REPORT,
  DEPLOY_READ,
  PAUSE;

  /** Every kind a stop can stand in but a human's pause. */
  public static StopKind[] withoutPause() {
    return Arrays.stream(values()).filter(kind -> kind != PAUSE).toArray(StopKind[]::new);
  }

  public static StopKind of(@Nullable TaskStop stop) {
    return stop == null ? NONE : of(stop.trigger().ends());
  }

  public static StopKind of(TaskStop.Trigger.Ends ends) {
    return valueOf(ends.name());
  }
}
