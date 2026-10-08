package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Retires a task: session killed, worktree and state entry removed. The branch survives. */
@Service
@RequiredArgsConstructor
public class TaskRetirement {

    private final StateService stateService;
    private final ConfigService configService;
    private final AgentSessions sessions;
    private final RetiredWorktrees worktrees;

    public String retire(String taskIdOrAlias) {
        String taskId = stateService.canonicalTaskId(taskIdOrAlias);
        TaskState task = stateService.task(taskId)
                .orElseThrow(() -> Refusal.noSuchTask(taskId));
        // First: removing a worktree under a live process's cwd leaves an agent grinding in a deleted directory.
        sessions.killWindows(taskId);
        boolean anyProjectMissing = worktrees.remove(taskId, task.repos(), configService.load().projects());
        stateService.removeTask(taskId);
        boolean closedViewer = sessions.closeViewerIfNoTasksLeft();
        return "Task " + taskId + " removed: worktree deleted, state entry dropped. Branch '" + taskId
                + "' was kept"
                + (anyProjectMissing ? " (worktree left on disk: project missing from jagt.yml)" : "")
                + (closedViewer ? ". Last task gone — the agents window was closed." : "");
    }
}
