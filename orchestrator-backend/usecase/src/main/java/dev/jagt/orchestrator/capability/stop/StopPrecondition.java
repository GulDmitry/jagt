package dev.jagt.orchestrator.capability.stop;

import dev.jagt.orchestrator.flow.capability.CapabilityInterceptor;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/** Refuses {@code action} on a task jagt stopped, unless a human pressed it. */
public record StopPrecondition(TaskAction action, TaskStops stops)
    implements CapabilityInterceptor {

  private static final Set<ActionOrigin> HUMAN =
      EnumSet.of(ActionOrigin.BOARD, ActionOrigin.PALETTE);

  @Override
  public int order() {
    return -1;
  }

  @Override
  public Outcome around(String taskId, Supplier<Outcome> work) {
    if (!HUMAN.contains(OriginContext.current()) && stops.stopped(taskId)) {
      throw Refusal.byState(
          action.id()
              + " "
              + taskId
              + " refused: jagt stopped it;"
              + " only a human presses it until `lift`");
    }
    return work.get();
  }
}
