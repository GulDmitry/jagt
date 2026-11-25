package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.flow.capability.CapabilityInterceptor;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.worktree.TaskSpecs;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The ship's commit is the session's, so the change is folded into the specs before the session is
 * told to make it.
 */
@Component
@RequiredArgsConstructor
public class SpecFold implements CapabilityInterceptor {

  private final StateService stateService;
  private final TaskSpecs specs;

  @Override
  public TaskAction action() {
    return TaskAction.SHIP;
  }

  @Override
  public Outcome around(String taskId, Supplier<Outcome> work) {
    stateService.task(taskId).ifPresent(task -> specs.fold(taskId, task));
    return work.get();
  }
}
