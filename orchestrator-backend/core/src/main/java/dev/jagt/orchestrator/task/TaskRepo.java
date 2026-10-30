package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.With;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One repository a task works in: the worktree cut for it, and the per-repo facts that follow from having its own
 * git remote. What multiplies per repository is worktrees, not sessions. {@code mrUrl} is the review request for
 * THIS repo; {@code merged} that git last found the request's branch inside its target, or the host said the request merged,
 * which {@code hostMerged} records so that git's later answer leaves it standing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskRepo(@Nullable String project, @Nullable String worktreePath, @With @Nullable String remoteUrl,
                       @Nullable String mrUrl, @Nullable Boolean merged, @Nullable Boolean hostMerged) {

    public TaskRepo(@Nullable String project, @Nullable String worktreePath, @Nullable String remoteUrl,
                    @Nullable String mrUrl) {
        this(project, worktreePath, remoteUrl, mrUrl, null, null);
    }

    /** A state file written before the remote was stripped still carries the credential; `/state` serves it. */
    @JsonCreator
    public TaskRepo {
        remoteUrl = GitRemote.withoutCredentials(remoteUrl);
    }

    public static TaskRepo of(@Nullable String project, @Nullable String worktreePath) {
        return new TaskRepo(project, worktreePath, null, null);
    }

    public TaskRepo withMrUrl(@Nullable String mrUrl) {
        boolean same = Objects.equals(this.mrUrl, mrUrl);
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, wasMerged() && same, wasHostMerged() && same);
    }

    /** Git's answer; the host's word, once given, stands. */
    public TaskRepo withMerged(boolean merged) {
        return wasHostMerged() ? this
                : new TaskRepo(project, worktreePath, remoteUrl, mrUrl, merged ? Boolean.TRUE : null, null);
    }

    public TaskRepo withMergedByHost() {
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, Boolean.TRUE, Boolean.TRUE);
    }

    @JsonIgnore
    public boolean wasHostMerged() {
        return Boolean.TRUE.equals(hostMerged);
    }

    @JsonIgnore
    public boolean wasMerged() {
        return Boolean.TRUE.equals(merged);
    }

    public boolean hasReviewRequest() {
        return mrUrl != null && !mrUrl.isBlank();
    }
}
