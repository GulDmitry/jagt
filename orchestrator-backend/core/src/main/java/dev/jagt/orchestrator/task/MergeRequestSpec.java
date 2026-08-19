package dev.jagt.orchestrator.task;

/**
 * The review request jagt wants to exist for a task branch — everything it takes to create one, and nothing
 * a host would have to guess. Host-neutral on purpose: GitLab calls it a merge request, GitHub a pull
 * request, and only the {@link dev.jagt.orchestrator.port.CodeHost} implementation knows the difference.
 *
 * @param remoteUrl          the repository's git remote (either shape); the host both identifies the project
 *                           by it and refuses a repository it does not own
 * @param sourceBranch       the task branch, already pushed
 * @param targetBranch       what it should merge into (the project's base branch), without an {@code origin/}
 * @param title              the exact title, already expanded from {@code codeReview.mrTitlePattern}
 * @param removeSourceBranch delete the task branch on merge
 * @param squash             squash the branch's commits on merge
 */
public record MergeRequestSpec(String remoteUrl, String sourceBranch, String targetBranch, String title,
                               boolean removeSourceBranch, boolean squash) {
}
