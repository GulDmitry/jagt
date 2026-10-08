package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.GitWorktrees;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Every checkout a retired task leaves, in every repository: nothing else would ever delete them. */
@Component
@RequiredArgsConstructor
@Slf4j
class RetiredWorktrees {

    private final GitWorktrees gitWorktrees;
    private final EditorDriver editorDriver;
    private final AgentSessions sessions;

    /** Whether a repository's project had left the configuration, its worktree then staying on disk. */
    boolean remove(String taskId, List<TaskRepo> repos, Map<String, ProjectConfig> projects) {
        boolean anyProjectMissing = false;
        for (TaskRepo repo : repos) {
            // Before the project lookup: a project deleted from jagt.yml is exactly when a stale registration
            // would be left behind.
            editorDriver.forgetProject(Path.of(repo.worktreePath()));
            ProjectConfig project = projects.get(repo.project());
            if (project == null) {
                anyProjectMissing = true;
                log.atWarn().setMessage("worktree removal skipped")
                        .addKeyValue("task", taskId)
                        .addKeyValue("project", repo.project())
                        .addKeyValue("cause", "not in jagt.yml")
                        .log();
                continue;
            }
            Path projectPath = Path.of(project.path());
            // The agent's record of the worktree goes too, or the next session there stops at the prompt it answers.
            sessions.forgetWorktree(Path.of(repo.worktreePath()));
            gitWorktrees.removeWorktree(projectPath, Path.of(repo.worktreePath()), null);
            // An abandoned deploy conflict leaves a jagt-deploy-* worktree and branch behind.
            gitWorktrees.removeDeployWorktreeIfPresent(projectPath, taskId);
            // A diff opened from the board cuts throwaway checkouts in the temp directory; nothing else ends them.
            gitWorktrees.removeDiffWorktrees(projectPath, taskId, repo.project());
            editorDriver.forgetProject(GitDeploy.deployWorktreePath(projectPath, taskId));
        }
        return anyProjectMissing;
    }
}
