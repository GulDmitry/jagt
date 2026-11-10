package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskMovedOn;
import dev.jagt.orchestrator.task.Step;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Tells a task's session about the deploy conflict waiting in its worktree. */
@Component
@RequiredArgsConstructor
@Slf4j
class ConflictHandover {

  private final AgentSessions sessions;
  private final StepRelay relay;

  /** Whether the session was told; false where the task moved on meanwhile. */
  boolean hand(String taskId, DeployConflicts.WaitingConflict conflict) {
    AgentRuntime.Admission admission = sessions.admit(taskId, conflict.worktree());
    log.atInfo()
        .setMessage("deploy conflict handed to session")
        .addKeyValue("task", taskId)
        .addKeyValue("worktree", conflict.worktree())
        .addKeyValue("admission", admission)
        .log();
    try {
      relay.write(taskId, Step.CONFLICT, told(taskId, conflict), conflict.read());
      return true;
    } catch (TaskMovedOn dropped) {
      return false;
    }
  }

  private static String told(String taskId, DeployConflicts.WaitingConflict conflict) {
    if (conflict.revert()) {
      return taskId
          + " did not revert: reverting it on the deploy branch conflicts in "
          + conflict.worktree()
          + ".";
    }
    return conflict.red() == null
        ? taskId
            + " did not deploy: merging it into the deploy branch conflicts in "
            + conflict.worktree()
            + "."
        : taskId
            + " deployed, but its "
            + conflict.red()
            + ". Fix the merge result in "
            + conflict.worktree()
            + ", the deploy branch as it stands.";
  }
}
