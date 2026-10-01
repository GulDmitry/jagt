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
        List<String> verifyCommand,
        // One line saying what this repository IS, for placing an item no label places. Blank = its path.
        String about,
        // How the agent checks a deploy where it landed, in the human's words. Blank = nobody is asked to.
        String deployCheck
) {

    /** A project declaring no command of its own: the four keys every install has always had. */
    public ProjectConfig(String path, String baseBranch, String deployBranch, List<String> labels) {
        this(path, baseBranch, deployBranch, labels, List.of(), null, null);
    }

    public ProjectConfig(String path, String baseBranch, String deployBranch, List<String> labels,
                         List<String> verifyCommand) {
        this(path, baseBranch, deployBranch, labels, verifyCommand, null);
    }

    public ProjectConfig(String path, String baseBranch, String deployBranch, List<String> labels,
                         List<String> verifyCommand, String about) {
        this(path, baseBranch, deployBranch, labels, verifyCommand, about, null);
    }

    public ProjectConfig withDeployCheck(String deployCheck) {
        return new ProjectConfig(path, baseBranch, deployBranch, labels, verifyCommand, about, deployCheck);
    }

    /** What a router is told this repository is; its path where nobody wrote a line, the name carrying most. */
    public String aboutOrPath() {
        return about == null || about.isBlank() ? String.valueOf(path) : about;
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
