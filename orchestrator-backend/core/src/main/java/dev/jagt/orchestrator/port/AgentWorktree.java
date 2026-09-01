package dev.jagt.orchestrator.port;

import java.nio.file.Path;
import java.util.List;

/**
 * What a runtime needs to provision one task's worktree. {@code outputStyle} and {@code disabledPlugins} are HINTS
 * a runtime whose CLI has no such notion ignores; null {@code outputStyle} = the agent's own default.
 */
public record AgentWorktree(Path path, Path orchestratorRoot, String outputStyle, List<String> disabledPlugins) {

    public AgentWorktree {
        disabledPlugins = disabledPlugins == null ? List.of() : List.copyOf(disabledPlugins);
    }
}
