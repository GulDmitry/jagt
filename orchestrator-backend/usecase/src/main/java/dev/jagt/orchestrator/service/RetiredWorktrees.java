package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The checkouts of repositories a task no longer has, which nothing else would ever delete. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetiredWorktrees {

    private final GitWorktrees gitWorktrees;
    private final AgentSessions sessions;

    /** Whether a repository's project had left the configuration, its worktree then staying on disk. */
    public boolean remove(String taskId, List<TaskRepo> repos, Map<String, ProjectConfig> projects) {
        boolean anyProjectMissing = false;
        for (TaskRepo repo : repos) {
            // The agent's record of the worktree goes too, or the next session there stops at the prompt it answers.
            sessions.forgetWorktree(Path.of(repo.worktreePath()));
            ProjectConfig project = projects.get(repo.project());
            if (project == null) {
                anyProjectMissing = true;
                gitWorktrees.discardDiffWorktrees(taskId, Objects.requireNonNull(repo.project()));
                log.atWarn().setMessage("worktree removal skipped")
                        .addKeyValue("task", taskId)
                        .addKeyValue("project", repo.project())
                        .addKeyValue("cause", "not in jagt.yml")
                        .log();
                continue;
            }
            Path projectPath = Path.of(project.path());
            gitWorktrees.removeWorktree(projectPath, Path.of(repo.worktreePath()), null);
            // An abandoned deploy conflict leaves a jagt-deploy-* worktree and branch behind.
            gitWorktrees.removeDeployWorktreeIfPresent(projectPath, taskId);
            // A diff opened from the board cuts throwaway checkouts in the temp directory; nothing else ends them.
            gitWorktrees.removeDiffWorktrees(projectPath, taskId, Objects.requireNonNull(repo.project()));
        }
        return anyProjectMissing;
    }

    /**
     * The trusted git entries of {@code leaving}'s checkout that no other repository of {@code task} also holds;
     * none where git cannot be read, so the trust stays as it was.
     */
    public Set<String> trustedOnlyBy(String taskId, TaskState task, TaskRepo leaving) {
        try {
            Set<String> own = new HashSet<>(gitWorktrees.gitState(worktreesOf(List.of(leaving))).keySet());
            List<TaskRepo> staying = task.repos().stream()
                    .filter(repo -> !Objects.equals(repo.project(), leaving.project())).toList();
            own.removeAll(gitWorktrees.gitState(worktreesOf(staying)).keySet());
            return own;
        } catch (RuntimeException unreadable) {
            log.atWarn().setMessage("git state of leaving repository not read").addKeyValue("task", taskId)
                    .addKeyValue("project", leaving.project()).addKeyValue("cause", unreadable.toString())
                    .addKeyValue("effect", "its trusted entries stay on the task").log();
            return Set.of();
        }
    }

    private static List<Path> worktreesOf(List<TaskRepo> repos) {
        return repos.stream().map(TaskRepo::worktreePath).filter(path -> path != null && !path.isBlank())
                .map(Path::of).toList();
    }
}
