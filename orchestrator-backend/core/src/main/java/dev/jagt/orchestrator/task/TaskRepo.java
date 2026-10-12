package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

/**
 * One repository a task works in: the worktree cut for it, and the per-repo facts that follow from having its own
 * git remote. What multiplies per repository is worktrees, not sessions. {@code mrUrl} is the review request for
 * THIS repo; {@code deployCommit} the merge commit {@code deploy} created in it, which {@code revert} undoes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskRepo(@Nullable String project, @Nullable String worktreePath, @Nullable String remoteUrl,
                       @Nullable String mrUrl, @Nullable String deployCommit) {

    /** A state file written before the remote was stripped still carries the credential; `/state` serves it. */
    public TaskRepo {
        remoteUrl = GitRemote.withoutCredentials(remoteUrl);
    }

    public static TaskRepo of(@Nullable String project, @Nullable String worktreePath) {
        return new TaskRepo(project, worktreePath, null, null, null);
    }

    public TaskRepo withRemoteUrl(@Nullable String remoteUrl) {
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit);
    }

    public TaskRepo withMrUrl(@Nullable String mrUrl) {
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit);
    }

    public TaskRepo withDeployCommit(@Nullable String deployCommit) {
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit);
    }

    public boolean hasReviewRequest() {
        return mrUrl != null && !mrUrl.isBlank();
    }
}
