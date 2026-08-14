package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.platform.EditorDriver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/** Retires a task: session killed, worktree and state entry removed. The branch survives. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskRetirement {

    private final StateService stateService;
    private final ConfigService configService;
    private final GitService gitService;
    private final EditorDriver editorDriver;
    private final AgentSessions sessions;

    public String retire(String taskIdOrAlias) {
        String taskId = stateService.canonicalTaskId(taskIdOrAlias);
        TaskState task = stateService.task(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task " + taskId + " not found in state.json"));
        // First: removing a worktree under a live process's cwd leaves a zombie agent grinding in a deleted
        // directory.
        sessions.killWindows(taskId);
        ProjectConfig project = configService.load().projects().get(task.project());
        if (project != null) {
            Path projectPath = Path.of(project.path());
            gitService.removeWorktree(projectPath, Path.of(task.worktreePath()), null);
            // An abandoned deploy conflict leaves a jagt-deploy-* worktree and branch behind.
            gitService.removeDeployWorktreeIfPresent(projectPath, taskId);
            editorDriver.forgetProject(GitService.deployWorktreePath(projectPath, taskId));
        } else {
            log.warn("Project '{}' of task {} no longer in config.json; skipping worktree removal",
                    task.project(), taskId);
        }
        editorDriver.forgetProject(Path.of(task.worktreePath()));
        stateService.removeTask(taskId);
        // Reserved by default: a viewer placed by hand survives task cycles.
        boolean closedViewer = sessions.closeViewerIfNoTasksLeft();
        return "Task " + taskId + " removed: worktree deleted, state entry dropped. Branch '" + taskId
                + "' was kept"
                + (project == null ? " (worktree left on disk: project missing from config.json)" : "")
                + (closedViewer ? ". Last task gone — the agents window was closed." : "");
    }
}
