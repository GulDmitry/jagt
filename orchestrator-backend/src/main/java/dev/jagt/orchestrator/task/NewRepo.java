package dev.jagt.orchestrator.task;

import java.nio.file.Path;

/**
 * One repository of a task being created, with every fact resolved before anything is cut — so a bad project or
 * a base branch that does not exist is answered while there is still nothing to clean up.
 *
 * @param primary whether the agent's session runs here; the others it edits in place from there
 */
public record NewRepo(String project, ProjectConfig config, Path projectPath, Path worktreePath,
                      Path gitCommonDir, String baseBranch, String remoteUrl, boolean primary) {

    /** The same repository as {@code state.json} keeps it: no request and no deploy yet. */
    public TaskRepo registered() {
        return new TaskRepo(project, worktreePath.toString(), remoteUrl, null, null);
    }
}
