package dev.jagt.orchestrator.task;

/**
 * The facts needed to adopt a review request that is already open: which branch it comes from, which one it
 * merges into, and what it is called.
 *
 * <p>Deliberately host- and source-agnostic: the same facts come either from a code host's own API
 * ({@link dev.jagt.orchestrator.port.CodeHost}, free) or from a headless assistant read
 * ({@link dev.jagt.orchestrator.port.MasterAssistant}, paid), and no consumer may care which.
 *
 * @param exists       false = the URL could not be resolved at all (gone, or the read failed)
 * @param sourceBranch the branch the request is built on, which becomes the task itself
 * @param targetBranch the branch the request merges into, which the next round must keep targeting
 */
public record MergeRequestFacts(boolean exists, String sourceBranch, String targetBranch, String title) {
}
