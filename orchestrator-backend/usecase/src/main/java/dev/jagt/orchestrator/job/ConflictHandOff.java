package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.ActionOrigin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One waiting deploy conflict: asked of its session once, the deploy finished once it is staged in full. */
@Component
@RequiredArgsConstructor
@Slf4j
class ConflictHandOff {

    private final AgentSessions sessions;
    private final CommandService commands;
    private final Map<String, String> asked = new ConcurrentHashMap<>();

    void handle(String taskId, DeployConflicts.WaitingConflict conflict) {
        if (conflict.resolved()) {
            asked.remove(taskId);
            log.atInfo().setMessage("master finishes deploy").addKeyValue("task", taskId)
                    .addKeyValue("worktree", conflict.worktree()).log();
            OriginContext.as(ActionOrigin.MASTER, () -> commands.execute(taskId, TaskAction.DEPLOY));
            return;
        }
        String brief = brief(taskId, conflict.worktree());
        if (!brief.equals(asked.get(taskId))) {
            sessions.relayIfChanged(taskId, brief);
            asked.put(taskId, brief);
        }
    }

    private static String brief(String taskId, Path worktree) {
        return taskId + " did not deploy: merging it into the deploy branch conflicts in " + worktree + ".\n\n"
                + "Resolve it there, never in your own worktree, keeping what both sides meant. Build or test what"
                + " the project lets you, then `git add` every resolved path in one go: once nothing is unmerged"
                + " and nothing is left unstaged, jagt finishes the deploy. Do not commit, push or report a status."
                + " If a side cannot be kept without a human's call, report outcome=question.";
    }
}
