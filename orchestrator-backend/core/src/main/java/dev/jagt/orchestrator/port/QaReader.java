package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaVerdict;

import java.nio.file.Path;
import java.util.List;

/**
 * One read of a deployed change by a fresh model, able to read and GET but to write nothing. Metered, and asked once per deploy.
 */
public interface QaReader {

    /** {@code model} blank inherits the agent CLI's own; {@code environments} are the only URLs the read may fetch. */
    record Run(String shared, String prompt, Path worktree, String sha, String model, List<String> environments) {
    }

    Answer<QaVerdict> read(Run run);

    /** The same read, of the reason the session gave for changing nothing, or of an arbiter's ruling. */
    Answer<QaDecline> reconsider(Run run);
}
