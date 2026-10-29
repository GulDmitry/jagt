package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.With;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One repository a task works in: the worktree cut for it, and the per-repo facts that follow from having its own
 * git remote. What multiplies per repository is worktrees, not sessions. {@code mrUrl} is the review request for
 * THIS repo; {@code deployCommit} the merge commit {@code deploy} created in it, which {@code revert} undoes with the
 * {@code deployFixes} committed on top of it, oldest first;
 * {@code merged} that git last found the request's branch inside its target, or the host said the request merged,
 * which {@code hostMerged} records so that git's later answer leaves it standing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskRepo(@Nullable String project, @Nullable String worktreePath, @With @Nullable String remoteUrl,
                       @Nullable String mrUrl, @Nullable String deployCommit,
                       @Nullable Boolean merged, @Nullable Boolean hostMerged,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> deployFixes) {

    public TaskRepo(@Nullable String project, @Nullable String worktreePath, @Nullable String remoteUrl,
                    @Nullable String mrUrl, @Nullable String deployCommit) {
        this(project, worktreePath, remoteUrl, mrUrl, deployCommit, null, null, List.of());
    }

    /** A state file written before the remote was stripped still carries the credential; `/state` serves it. */
    @JsonCreator
    public TaskRepo {
        remoteUrl = GitRemote.withoutCredentials(remoteUrl);
        deployFixes = deployFixes == null ? List.of() : List.copyOf(deployFixes);
    }

    public static TaskRepo of(@Nullable String project, @Nullable String worktreePath) {
        return new TaskRepo(project, worktreePath, null, null, null);
    }

    public TaskRepo withMrUrl(@Nullable String mrUrl) {
        boolean same = Objects.equals(this.mrUrl, mrUrl);
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit,
                wasMerged() && same, wasHostMerged() && same, deployFixes);
    }

    /** Git's answer; the host's word, once given, stands. */
    public TaskRepo withMerged(boolean merged) {
        return wasHostMerged() ? this
                : new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit, merged ? Boolean.TRUE : null, null, deployFixes);
    }

    public TaskRepo withMergedByHost() {
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit, Boolean.TRUE, Boolean.TRUE, deployFixes);
    }

    /** A new deploy merge: what {@code revert} takes out, with no fix on top of it yet. */
    public TaskRepo withMerge(@Nullable String deployCommit) {
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit, merged, hostMerged, List.of());
    }

    /** A fix committed on the deploy branch after the merge; a deploy that recorded no merge takes it as the merge. */
    public TaskRepo withDeployFix(String fix) {
        if (deployCommit == null || deployCommit.isBlank()) {
            return withMerge(fix);
        }
        List<String> all = new ArrayList<>(deployFixes);
        all.add(fix);
        return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, deployCommit, merged, hostMerged, all);
    }

    /** The commit last pushed to the deploy branch for this task, which its pipeline runs on. */
    @JsonIgnore
    public @Nullable String deployTip() {
        return deployFixes.isEmpty() ? deployCommit : deployFixes.getLast();
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
