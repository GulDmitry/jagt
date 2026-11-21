package dev.jagt.orchestrator.port;

import java.util.Optional;

/** Why git must not run for a task yet, as a read of what git obeys now just found. */
@FunctionalInterface
public interface GitDistrust {

  /**
   * Empty where git obeys what a human trusted; a refusal is thrown where the state cannot be read.
   */
  Optional<String> distrusted(String taskId);
}
