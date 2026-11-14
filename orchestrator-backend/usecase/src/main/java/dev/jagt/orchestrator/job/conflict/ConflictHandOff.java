package dev.jagt.orchestrator.job.conflict;

import dev.jagt.orchestrator.job.deploy.DeployConflicts;
import dev.jagt.orchestrator.service.turn.StepRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * One waiting deploy conflict: handed to its session once, the deploy finished once it is staged in
 * full.
 */
@Component
@RequiredArgsConstructor
class ConflictHandOff {

  private final StepRelay relay;
  private final ConflictHandover handover;
  private final ConflictStall stall;
  private final ConflictFinish finish;

  void handle(String taskId, DeployConflicts.WaitingConflict conflict) {
    if (conflict.resolved()) {
      finish.press(taskId, conflict.worktree());
      return;
    }
    if (!relay.standing(taskId).equals(conflict.read())) {
      return;
    }
    if (!stall.handed(taskId) && !handover.hand(taskId, conflict)) {
      return;
    }
    stall.watch(taskId, conflict);
  }
}
