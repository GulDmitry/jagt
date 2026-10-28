package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One repository a task works in: the worktree cut for it, and the per-repo facts that follow from having its own
 * git remote. What multiplies per repository is worktrees, not sessions. {@code mrUrl} is the review request for
 * THIS repo; {@code deployCommit} the merge commit {@code deploy} created in it, which {@code revert} undoes;
 * {@code merged} that git last found the request's branch inside its target.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskRepo(@Nullable String project, @Nullable String worktreePath, @Nullable String remoteUrl,
                       @Nullable String mrUrl, @Nullable String deployCommit,
                       @Nullable Boolean merged) {

    public TaskRepo(@Nullable String project, @Nullable String worktreePath, @Nullable String remoteUrl,
                    @Nullable String mrUrl, @Nullable String deployCommit) {
        this(project, worktreePath, remoteUrl, mrUrl, deployCommit, null);
    }

    /** A state file written before the remote was stripped still carries the credential; `/state` serves it. */
    @JsonCreator
    public TaskRepo {
        remoteUrl = GitRemote.withoutCredentials(remoteUrl);
    }

    public static TaskRepo of(@Nullable String project, @Nullable String worktreePath) {
        return new TaskRepo(project, worktreePath, null, null, null);
    }

    public TaskRepo withRemoteUrl(@Nullable String remoteUrl) {
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit, merged);
    }

    public TaskRepo withMrUrl(@Nullable String mrUrl) {
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit, wasMerged() && Objects.equals(this.mrUrl, mrUrl));
    }

    public TaskRepo withDeployCommit(@Nullable String deployCommit) {
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit, merged);
    }

    public TaskRepo withMerged(boolean merged) {
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit, merged ? Boolean.TRUE : null);
    }

    @JsonIgnore
    public boolean wasMerged() {
        return Boolean.TRUE.equals(merged);
    }

    public boolean hasReviewRequest() {
        return mrUrl != null && !mrUrl.isBlank();
    }
}
