package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.EditorDriver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * The only two operations that write a SHARED branch: merging a task into the deploy branch, and taking that
 * merge back out. Neither checks that the caller is the human; that gate sits outside.
 */
@Service
@RequiredArgsConstructor
public class DeployService {

    private final StateService stateService;
    private final ConfigService configService;
    private final GitService gitService;
    private final EditorDriver editorDriver;

    private TaskState requireTask(String taskId) {
        return stateService.task(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task " + taskId + " not found in state.json"));
    }

    /** Merges the task branch into the project's deploy branch and pushes it. */
    public String deploy(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        ProjectConfig project = deployTarget(task);
        String deployBranch = project.deployBranch();
        String merged;
        try {
            merged = gitService.mergeIntoAndPush(Path.of(project.path()), taskId, deployBranch);
        } catch (GitService.MergeConflictException e) {
            // Resolve on the DEPLOY side, never in the task branch: the request targets the base branch,
            // so merging the deploy branch into the task branch would balloon its diff with everything the
            // deploy branch carries. jagt does NOT auto-open an editor — the dashboard flags DEPLOY_CONFLICT,
            // the human opens the worktree and resolves it, then deploys again (the backend does the push).
            stateService.updateTask(taskId,
                    t -> t.withStatus(TaskStatus.DEPLOY_CONFLICT, "resolve conflict in " + e.deployWorktree()));
            return "deploy " + taskId + ": CONFLICT into " + deployBranch + ", nothing pushed. Resolve in "
                    + e.deployWorktree() + " (`git add`), then `deploy " + taskId + "` again.";
        }
        // The deploy worktree is gone once pushed; drop it from the editor's recent-projects list too, so a
        // human who opened it to resolve a conflict isn't left with a dead jagt-deploy entry.
        editorDriver.forgetProject(GitService.deployWorktreePath(Path.of(project.path()), taskId));
        // deploy IS a state transition — mark it so the dashboard's next move is 'done', not 'sweep'.
        stateService.updateTask(taskId, t -> t.withStatus(TaskStatus.DEPLOYED, "deployed to " + deployBranch)
                // Recorded in the SAME update as the status: a deploy whose commit went missing is a deploy
                // `revert` can only send to the by-hand path, and these two facts are one event.
                .withDeployCommit(merged));
        return "Merged " + taskId + " into " + deployBranch + " (" + shortSha(merged) + "); DEPLOYED";
    }

    /**
     * Undoes one deploy: reverts the merge commit it created on the deploy branch and pushes the revert. The
     * task branch keeps all its commits, so the normal follow-up is "fix and ship again".
     */
    public String revert(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        if (task.status() != TaskStatus.DEPLOYED) {
            throw new IllegalArgumentException("revert " + taskId + ": cannot revert a " + task.status()
                    + " task — only a DEPLOYED one has a merge to undo.");
        }
        ProjectConfig project = deployTarget(task);
        String deployBranch = project.deployBranch();
        String mergeCommit = task.deployCommit();
        if (mergeCommit == null || mergeCommit.isBlank()) {
            // Deployed before jagt started recording the commit. Guessing it (search the log by branch name)
            // would risk reverting the WRONG merge on a shared branch — the one mistake with no cheap undo.
            throw new IllegalStateException("revert " + taskId + ": jagt has no record of which commit this"
                    + " deploy created (it predates that being stored), and guessing on a shared branch is not"
                    + " something it will do. Revert by hand: `git log --merges --grep " + taskId + " origin/"
                    + deployBranch + "` to find the merge, then `git revert -m 1 <sha>` and push.");
        }
        String revertCommit = gitService.revertMergeAndPush(Path.of(project.path()), taskId, deployBranch,
                mergeCommit);
        stateService.updateTask(taskId, t -> t.withStatus(TaskStatus.REVERTED,
                "reverted on " + deployBranch + " (" + shortSha(revertCommit) + ")"));
        return "Reverted " + taskId + " on " + deployBranch + " (" + shortSha(revertCommit)
                + "); REVERTED — fix and ship again, or `done`.";
    }

    /**
     * The project a task deploys to, with the guard both deploy and revert need. HARD SAFETY: the deploy
     * branch must NEVER be the base/release branch tasks are cut from — jagt writes to exactly one shared
     * branch and it is not that one.
     */
    private ProjectConfig deployTarget(TaskState task) {
        if (task.repos().size() > 1) {
            // Half a change on a shared branch is worse than none: the repositories move together or not at
            // all, and jagt cannot promise that yet — one merge landing while the next conflicts would leave
            // the deploy branch carrying one side of a contract.
            throw new IllegalArgumentException("REFUSED: " + String.join(", ", task.projects())
                    + " move together, and jagt cannot land half a change on a shared branch — merge them"
                    + " yourself.");
        }
        ProjectConfig project = configService.project(task.project());
        if (project.deployBranch() == null || project.deployBranch().isBlank()) {
            throw new IllegalArgumentException("Project '" + task.project()
                    + "' has no deployBranch in config.json — set it to enable deploy");
        }
        String base = project.baseBranch() == null ? "" : project.baseBranch().replaceFirst("^origin/", "");
        if (project.deployBranch().equals(base)) {
            throw new IllegalArgumentException("REFUSED: deployBranch equals the base branch '" + base
                    + "'. jagt must never merge into the branch tasks are created from — point deployBranch"
                    + " at a downstream branch (e.g. dev).");
        }
        return project;
    }

    /** Commits are shown short everywhere a human reads one: eight characters identify it and fit a line. */
    private static String shortSha(String sha) {
        return sha == null || sha.length() < 8 ? String.valueOf(sha) : sha.substring(0, 8);
    }
}
