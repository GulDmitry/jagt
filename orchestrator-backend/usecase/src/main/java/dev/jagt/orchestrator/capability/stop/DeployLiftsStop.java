package dev.jagt.orchestrator.capability.stop;

import dev.jagt.orchestrator.flow.capability.CapabilityInterceptor;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Lifts the stop standing on a task a human deployed, so the landed change is checked like any
 * other.
 */
public record DeployLiftsStop(TaskStops stops) implements CapabilityInterceptor {

  private static final Set<ActionOrigin> LIFTING =
      EnumSet.of(ActionOrigin.BOARD, ActionOrigin.PALETTE);

  @Override
  public TaskAction action() {
    return TaskAction.DEPLOY;
  }

  @Override
  public Outcome around(String taskId, Supplier<Outcome> work) {
    Outcome outcome = work.get();
    if (LIFTING.contains(OriginContext.current())) {
      if (stops.stopped(taskId)) {
        stops.lift(taskId);
      } else {
        stops.restoreMasterLifts(taskId);
      }
    }
    return outcome;
  }
}
