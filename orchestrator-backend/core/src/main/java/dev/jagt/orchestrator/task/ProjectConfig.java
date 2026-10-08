package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectConfig(
        String path,
        String baseBranch,
        String deployBranch,
        List<String> labels,
        // What jagt runs in the worktree before a hand-back reaches a human. Null or empty = nothing is run.
        List<String> verifyCommand,
        // One line saying what this repository IS, for placing an item no label places. Blank = its path.
        String about
) {

    private static final Pattern REF_PREFIX = Pattern.compile("^\\+?(refs/heads/|refs/remotes/origin/|origin/)?");

    /** A project declaring no command of its own: the four keys every install has always had. */
    public ProjectConfig(String path, String baseBranch, String deployBranch, List<String> labels) {
        this(path, baseBranch, deployBranch, labels, List.of(), null);
    }

    public ProjectConfig(String path, String baseBranch, String deployBranch, List<String> labels,
                         List<String> verifyCommand) {
        this(path, baseBranch, deployBranch, labels, verifyCommand, null);
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
        String deploy = localName(deployBranch);
        return !deploy.isEmpty() && deploy.equals(localName(baseBranch));
    }

    /** The branch a ref names, however spelled: {@code +refs/heads/main} and {@code origin/main} are {@code main}. */
    public static String localName(String ref) {
        return ref == null ? "" : REF_PREFIX.matcher(ref.strip()).replaceFirst("");
    }
}
