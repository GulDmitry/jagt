package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.GitService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.port.EditorDriver;
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
        // EVERY repository, not just the session's: the others hold a checkout of their own — and copies of the
        // local files worktree.copyGlobs brought in — which nothing else would ever delete.
        boolean anyProjectMissing = false;
        var projects = configService.load().projects();
        for (TaskRepo repo : task.repos()) {
            // Needs no project config, and a project deleted from config.json is exactly when a stale editor
            // registration would otherwise be left behind.
            editorDriver.forgetProject(Path.of(repo.worktreePath()));
            ProjectConfig project = projects.get(repo.project());
            if (project == null) {
                anyProjectMissing = true;
                log.atWarn().setMessage("worktree removal skipped")
                        .addKeyValue("task", taskId)
                        .addKeyValue("project", repo.project())
                        .addKeyValue("cause", "not in config.json")
                        .log();
                continue;
            }
            Path projectPath = Path.of(project.path());
            gitService.removeWorktree(projectPath, Path.of(repo.worktreePath()), null);
            // An abandoned deploy conflict leaves a jagt-deploy-* worktree and branch behind.
            gitService.removeDeployWorktreeIfPresent(projectPath, taskId);
            editorDriver.forgetProject(GitService.deployWorktreePath(projectPath, taskId));
        }
        stateService.removeTask(taskId);
        boolean closedViewer = sessions.closeViewerIfNoTasksLeft();
        return "Task " + taskId + " removed: worktree deleted, state entry dropped. Branch '" + taskId
                + "' was kept"
                + (anyProjectMissing ? " (worktree left on disk: project missing from config.json)" : "")
                + (closedViewer ? ". Last task gone — the agents window was closed." : "");
    }
}
