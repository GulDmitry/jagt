package dev.jagt.orchestrator.port;

import java.nio.file.Path;
import java.util.List;

/**
 * What a runtime needs to provision one task's worktree. {@code outputStyle}, {@code disabledPlugins} and
 * {@code allowedTools} are HINTS a runtime whose CLI has no such notion ignores; null {@code outputStyle} = the
 * agent's own default.
 */
public record AgentWorktree(Path path, Path orchestratorRoot, String outputStyle, List<String> disabledPlugins,
                            List<String> allowedTools) {

    public AgentWorktree {
        disabledPlugins = disabledPlugins == null ? List.of() : List.copyOf(disabledPlugins);
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    public AgentWorktree(Path path, Path orchestratorRoot, String outputStyle, List<String> disabledPlugins) {
        this(path, orchestratorRoot, outputStyle, disabledPlugins, List.of());
    }

    public AgentWorktree withAllowedTools(List<String> tools) {
        return new AgentWorktree(path, orchestratorRoot, outputStyle, disabledPlugins, tools);
    }
}
