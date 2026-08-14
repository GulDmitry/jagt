package dev.jagt.orchestrator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** What a human can do to one task on this machine: look at it, restart its agent, close it down. */
@Service
@RequiredArgsConstructor
public class TaskOperations {

    private final AgentSessions sessions;
    private final IdeLauncher ide;
    private final TaskRetirement retirement;

    public String focus(String taskId) {
        return sessions.focusTask(taskId);
    }

    public String respawnAgent(String taskId) {
        return sessions.openTaskTab(taskId, null);
    }

    public String openProject(String taskId) {
        return ide.open(taskId, "project");
    }

    public String openDiff(String taskId) {
        return ide.open(taskId, "diff");
    }

    public String retire(String taskId) {
        return retirement.retire(taskId);
    }
}
