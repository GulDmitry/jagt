package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectConfig(
        String path,
        String baseBranch,
        String deployBranch,
        List<String> labels,
        // What jagt runs in the worktree before a hand-back reaches a human. Null or empty = nothing is run.
        List<String> verifyCommand
) {

    /** A project declaring no command of its own: the four keys every install has always had. */
    public ProjectConfig(String path, String baseBranch, String deployBranch, List<String> labels) {
        this(path, baseBranch, deployBranch, labels, List.of());
    }

    /** The base branch as a LOCAL name, which is the form every other branch here is written in. */
    public String baseBranchName() {
        return baseBranch == null ? "" : baseBranch.replaceFirst("^origin/", "");
    }

    /** A deploy would merge into the branch tasks are cut from, which is the one write jagt must never make. */
    public boolean deploysIntoTheBaseBranch() {
        return deployBranch != null && !deployBranch.isBlank() && deployBranch.equals(baseBranchName());
    }
}
