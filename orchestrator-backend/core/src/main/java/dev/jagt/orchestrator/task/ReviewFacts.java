package dev.jagt.orchestrator.task;

import java.util.List;

/**
 * One review round: whether it is APPROVED, the latest pipeline result, and every UNRESOLVED discussion note
 * (bots + humans), each pre-formatted as one line for relaying to the agent.
 *
 * <p>Source-agnostic on purpose: the same facts come either from a code host's own API
 * ({@link dev.jagt.orchestrator.port.CodeHost}, free) or from a model read
 * ({@link dev.jagt.orchestrator.port.MasterAssistant}, paid), and no consumer may care which.
 *
 * @param exists   false = the request could not be read at all (gone, or the read failed)
 * @param openedAt when the HOST says the request was opened, 0 = unknown — a read that cannot answer it (the
 *                 model read) must not have jagt's own stamp substituted for it: "open since jagt noticed" is a
 *                 different fact, and wrong by days for a request a task was resumed on
 */
public record ReviewFacts(boolean exists, boolean approved, String pipelineStatus, List<String> comments,
                          long openedAt) {

    /** A round from a source that cannot say when the request was opened. */
    public ReviewFacts(boolean exists, boolean approved, String pipelineStatus, List<String> comments) {
        this(exists, approved, pipelineStatus, comments, 0);
    }
}
