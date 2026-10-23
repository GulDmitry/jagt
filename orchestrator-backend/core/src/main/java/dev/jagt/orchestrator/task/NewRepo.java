package dev.jagt.orchestrator.task;

import java.nio.file.Path;

/**
 * One repository of a task being created, with every fact resolved before anything is cut. {@code primary} says
 * whether the agent's session runs here; the others it edits in place from there.
 */
public record NewRepo(String project, ProjectConfig config, Path projectPath, Path worktreePath,
                      Path gitCommonDir, String baseBranch, String remoteUrl, boolean primary) {

    /** This repository joining a task whose session already runs in another. */
    public NewRepo besideTheSession() {
        return new NewRepo(project, config, projectPath, worktreePath, gitCommonDir, baseBranch, remoteUrl, false);
    }

    /** The same repository as {@code state.json} keeps it: no request and no deploy yet. */
    /** The same repository, worked in the worktree at {@code path}. */
    public NewRepo at(Path path) {
        return new NewRepo(project, config, projectPath, path, gitCommonDir, baseBranch, remoteUrl, primary);
    }

    public TaskRepo registered() {
        return TaskRepo.of(project, worktreePath.toString()).withRemoteUrl(remoteUrl);
    }
}
