package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One repository a task works in: its project key, the worktree cut for it, and the per-repo facts that follow
 * from having its own git remote — the review request opened for it and the merge commit a deploy created there.
 *
 * <p>A task has a LIST of these because one piece of work can legitimately span repositories (a PHP service, a
 * Java service and the contract between them), and ONE agent session should be able to change all of them: the
 * contract only makes sense if both sides move together. What multiplies is worktrees, not sessions — the agent
 * runs in the first repo and edits the others in place, and every jagt tool it calls resolves to this same task
 * from any of those directories.
 *
 * @param mrUrl        the review request for THIS repo; each repository gets its own, because each has its own
 *                     history and its own reviewers
 * @param deployCommit the merge commit {@code deploy} created in THIS repo, which is what {@code revert} undoes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskRepo(String project, String worktreePath, String remoteUrl, String mrUrl,
                       String deployCommit) {

    public static TaskRepo of(String project, String worktreePath) {
        return new TaskRepo(project, worktreePath, null, null, null);
    }

    public TaskRepo withRemoteUrl(String remoteUrl) {
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit);
    }

    public TaskRepo withMrUrl(String mrUrl) {
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit);
    }

    public TaskRepo withDeployCommit(String deployCommit) {
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit);
    }

    public boolean hasReviewRequest() {
        return mrUrl != null && !mrUrl.isBlank();
    }
}
