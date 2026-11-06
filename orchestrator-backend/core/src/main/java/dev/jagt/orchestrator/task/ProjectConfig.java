package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectConfig(
        String path,
        String baseBranch,
        String deployBranch,
        List<String> labels,
        // What jagt runs in the worktree before a hand-back reaches a human. Null or empty = nothing is run.
        @Nullable List<String> verifyCommand,
        // One line saying what this repository IS, for placing an item no label places. Blank = its path.
        @Nullable String about,
        @Nullable List<String> humanPaths,
        @Nullable String codeHost,
        @Nullable String codeHostName,
        @Nullable List<String> pipelineCommand,
        @Nullable List<String> retryCommand,
        @Nullable List<String> loginCommand,
        // The git remote branches are shared through; blank is origin, none a repository kept on this machine.
        @Nullable String remote,
        @Nullable List<String> closeCommand
) {

    /** The {@code codeHost} value of a repository with no host to open a request on or read a pipeline from. */
    public static final String NO_CODE_HOST = "none";

    /** A project declaring no command of its own: the four keys every install has always had. */
    public ProjectConfig(String path, String baseBranch, String deployBranch, List<String> labels) {
        this(path, baseBranch, deployBranch, labels, List.of(), null, null, null, null, null, null, null, null, null);
    }

    /** What a router is told this repository is; its path where nobody wrote a line, the name carrying most. */
    public String aboutOrPath() {
        return about == null || about.isBlank() ? String.valueOf(path) : about;
    }

    /** Repository paths, as globs, whose change only a human takes further. */
    public List<String> humanPathsOrNone() {
        return humanPaths == null ? List.of() : humanPaths;
    }

    public Remote gitRemote() {
        return Remote.of(remote);
    }

    /** The base branch as a LOCAL name, which is the form every other branch here is written in. */
    public String baseBranchName() {
        return gitRemote().branchOf(baseBranch);
    }

    /** A deploy would merge into the branch tasks are cut from, which is the one write jagt must never make. */
    public boolean deploysIntoTheBaseBranch() {
        String deploy = gitRemote().branchOf(deployBranch);
        return !deploy.isEmpty() && deploy.equals(baseBranchName());
    }
}
