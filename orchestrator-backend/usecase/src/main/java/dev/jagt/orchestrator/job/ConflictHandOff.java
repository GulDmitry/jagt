package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StepRelay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** One waiting deploy conflict: handed to its session once, the deploy finished once it is staged in full. */
@Component
@RequiredArgsConstructor
@Slf4j
class ConflictHandOff {

    private final AgentSessions sessions;
    private final StepRelay relay;
    private final ConflictStall stall;
    private final ConflictFinish finish;

    void handle(String taskId, DeployConflicts.WaitingConflict conflict) {
        if (conflict.resolved()) {
            finish.press(taskId, conflict.worktree());
            return;
        }
        if (!stall.handed(taskId)) {
            AgentRuntime.Admission admission = sessions.admit(taskId, conflict.worktree());
            log.atInfo().setMessage("deploy conflict handed to session").addKeyValue("task", taskId)
                    .addKeyValue("worktree", conflict.worktree()).addKeyValue("admission", admission).log();
            relay.write(taskId, Step.CONFLICT, told(taskId, conflict), relay.standing(taskId));
        }
        stall.watch(taskId, conflict);
    }

    private static String told(String taskId, DeployConflicts.WaitingConflict conflict) {
        if (conflict.revert()) {
            return taskId + " did not revert: reverting it on the deploy branch conflicts in " + conflict.worktree() + ".";
        }
        return conflict.red() == null
                ? taskId + " did not deploy: merging it into the deploy branch conflicts in " + conflict.worktree() + "."
                : taskId + " deployed, but its " + conflict.red() + ". Fix the merge result in " + conflict.worktree()
                        + ", the deploy branch as it stands.";
    }
}
