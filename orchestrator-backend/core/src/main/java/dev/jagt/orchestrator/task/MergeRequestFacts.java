package dev.jagt.orchestrator.task;

/**
 * The facts needed to adopt a review request that is already open. {@code exists=false} means the HOST says there
 * is no such request; a read that failed carries no facts at all, and no caller may merge the two.
 */
public record MergeRequestFacts(boolean exists, String sourceBranch, String targetBranch, String title) {
}
