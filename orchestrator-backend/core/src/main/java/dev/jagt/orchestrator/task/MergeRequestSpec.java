package dev.jagt.orchestrator.task;

/**
 * The review request jagt wants to exist for a task branch — everything it takes to create one, and nothing a
 * host would have to guess. What a host calls it is the host's own business.
 *
 * @param remoteUrl          the repository's git remote, in either shape
 * @param sourceBranch       the task branch, already pushed
 * @param targetBranch       what it should merge into, without an {@code origin/}
 * @param title              the exact title, already expanded
 * @param removeSourceBranch delete the task branch on merge
 * @param squash             squash the branch's commits on merge
 */
public record MergeRequestSpec(String remoteUrl, String sourceBranch, String targetBranch, String title,
                               boolean removeSourceBranch, boolean squash) {
}
