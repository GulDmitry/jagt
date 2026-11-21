package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.capability.CapabilityInterceptor;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.git.TrustedGitState;
import java.util.function.Supplier;

/** Refuses {@code action} while what the task's git obeys is not what a human trusted. */
public record GitStatePrecondition(TaskAction action, TrustedGitState trusted)
    implements CapabilityInterceptor {

  @Override
  public int order() {
    return -1;
  }

  @Override
  public Outcome around(String taskId, Supplier<Outcome> work) {
    trusted
        .distrust(taskId)
        .ifPresent(why -> {
          throw Refusal.byState(action.id() + " " + taskId + " refused: " + why);
        });
    return work.get();
  }
}
