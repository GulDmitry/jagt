package dev.jagt.orchestrator.model;

import java.util.List;

/**
 * One review round of a merge/pull request, as the sweep needs it: whether it is APPROVED, the latest
 * pipeline result, and every UNRESOLVED discussion note (bots + humans), each pre-formatted as one line for
 * relaying to the agent.
 *
 * <p>Deliberately host- and source-agnostic: the same facts come either from a code host's REST API
 * ({@link dev.jagt.orchestrator.codehost.CodeHost}, free) or from a headless assistant read
 * ({@link dev.jagt.orchestrator.assistant.MasterAssistant}, paid), and no consumer may care which.
 *
 * @param exists false = the request could not be read at all (gone, or the read failed)
 */
public record ReviewFacts(boolean exists, boolean approved, String pipelineStatus, List<String> comments) {
}
