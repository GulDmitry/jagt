package dev.jagt.orchestrator.port;

import java.nio.file.Path;
import java.util.List;
import lombok.With;

/**
 * What a runtime needs to provision one task's worktree; {@code allowedTools} is a hint a runtime
 * may ignore.
 */
public record AgentWorktree(
    Path path, Path orchestratorRoot, @With List<String> allowedTools, @With Sandbox sandbox) {

  public AgentWorktree {
    allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
  }

  public static AgentWorktree of(Path path, Path orchestratorRoot) {
    return new AgentWorktree(path, orchestratorRoot, List.of(), Sandbox.off());
  }
}
