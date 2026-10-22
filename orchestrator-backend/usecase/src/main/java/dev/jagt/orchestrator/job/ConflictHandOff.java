package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.AgentSessions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** One waiting deploy conflict: handed to its session once, the deploy finished once it is staged in full. */
@Component
@RequiredArgsConstructor
@Slf4j
class ConflictHandOff {

    private final AgentSessions sessions;
    private final ConflictStall stall;
    private final ConflictFinish finish;

    void handle(String taskId, DeployConflicts.WaitingConflict conflict) {
        if (conflict.resolved()) {
            finish.deploy(taskId, conflict.worktree());
            return;
        }
        if (!stall.handed(taskId)) {
            AgentRuntime.Admission admission = sessions.admit(taskId, conflict.worktree());
            log.atInfo().setMessage("deploy conflict handed to session").addKeyValue("task", taskId)
                    .addKeyValue("worktree", conflict.worktree()).addKeyValue("admission", admission).log();
            sessions.writeTaskContext(taskId, brief(taskId, conflict.worktree()));
        }
        stall.watch(taskId, conflict);
    }

    private static String brief(String taskId, Path worktree) {
        return taskId + " did not deploy: merging it into the deploy branch conflicts in " + worktree + ".\n\n"
                + "That worktree is yours until it deploys, and finishing its merge is not a `git merge` of yours."
                + " Resolve it there, never in your own worktree, keeping what both sides meant. Build or test what"
                + " the project lets you, then `git add` every resolved path in one go: once nothing is unmerged"
                + " and nothing is left unstaged, jagt finishes the deploy. Do not commit, push or report a status."
                + " If a side cannot be kept without a human's call, report outcome=question.";
    }
}
