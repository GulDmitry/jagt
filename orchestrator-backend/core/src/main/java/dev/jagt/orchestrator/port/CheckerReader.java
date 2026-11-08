package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaVerdict;
import java.nio.file.Path;
import java.util.List;

/**
 * One read of a deployed change by a fresh model, able to read and GET but to write nothing.
 * Metered, and asked once per deploy.
 */
public interface CheckerReader {

  /**
   * {@code model} blank inherits the agent CLI's own; {@code reach} is what the read may touch
   * beyond its worktree; {@code schema} is the JSON schema of the answer the role's definition
   * declares and {@code role} the id its report is named for.
   */
  record Run(
      String shared,
      String prompt,
      Path worktree,
      String sha,
      String model,
      Reach reach,
      String schema,
      String role) {}

  /** The only URLs the read may fetch and the extra roots it may read. */
  record Reach(List<String> urls, List<Path> paths) {}

  Answer<QaVerdict> read(Run run);

  /**
   * The same read, of the reason the session gave for changing nothing, or of an arbiter's ruling.
   */
  Answer<QaDecline> reconsider(Run run);
}
