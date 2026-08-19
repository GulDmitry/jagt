package dev.jagt.orchestrator.task;

/**
 * The facts needed to adopt a review request that is already open.
 *
 * <p>Source-agnostic on purpose: the same facts come either from a code host's own API
 * ({@link dev.jagt.orchestrator.port.CodeHost}, free) or from a model read
 * ({@link dev.jagt.orchestrator.port.MasterAssistant}, paid), and no consumer may care which.
 *
 * @param exists false = the URL could not be resolved at all (gone, or the read failed)
 */
public record MergeRequestFacts(boolean exists, String sourceBranch, String targetBranch, String title) {
}
