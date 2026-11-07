package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskMovedOn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What the Master does with a stop it decides: lift it, and send the session back to work where it
 * says how.
 */
@Component
@RequiredArgsConstructor
class MasterStopMoves {

  private final MasterPress press;
  private final MasterVerdicts verdicts;
  private final StateService stateService;

  /** False where the press was refused. */
  boolean lift(String taskId) {
    return press.lift(taskId);
  }

  /**
   * The task as the lift left it, told {@code instruction}; false where the telling was refused or
   * the task moved on.
   */
  boolean returned(String taskId, String instruction) {
    try {
      return stateService
          .task(taskId)
          .filter(task -> !task.stopped())
          .map(task -> verdicts.stopReturned(taskId, task, instruction))
          .orElse(false);
    } catch (TaskMovedOn moved) {
      return false;
    }
  }
}
