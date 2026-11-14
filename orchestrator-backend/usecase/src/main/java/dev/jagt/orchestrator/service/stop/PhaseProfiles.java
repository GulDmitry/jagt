package dev.jagt.orchestrator.service.stop;

import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.task.TaskState;

@FunctionalInterface
public interface PhaseProfiles {

  /**
   * What the Master may do on the task in the phase it stands in: none where it read the round or
   * left the task.
   */
  Arbiter.Profile at(TaskState task);
}
