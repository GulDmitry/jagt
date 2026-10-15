package dev.jagt.orchestrator.port;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * What a runtime needs to provision one task's worktree. {@code outputStyle}, {@code disabledPlugins} and
 * {@code allowedTools} are HINTS a runtime whose CLI has no such notion ignores; null {@code outputStyle} = the
 * agent's own default.
 */
public record AgentWorktree(Path path, Path orchestratorRoot, @Nullable String outputStyle, List<String> disabledPlugins,
                            List<String> allowedTools, Sandbox sandbox) {

    public AgentWorktree {
        disabledPlugins = disabledPlugins == null ? List.of() : List.copyOf(disabledPlugins);
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    public static AgentWorktree of(Path path, Path orchestratorRoot) {
        return new AgentWorktree(path, orchestratorRoot, null, List.of(), List.of(), Sandbox.off());
    }

    public AgentWorktree withOutputStyle(@Nullable String outputStyle) {
        return new AgentWorktree(path, orchestratorRoot, outputStyle, disabledPlugins, allowedTools, sandbox);
    }

    public AgentWorktree withDisabledPlugins(List<String> plugins) {
        return new AgentWorktree(path, orchestratorRoot, outputStyle, plugins, allowedTools, sandbox);
    }

    public AgentWorktree withAllowedTools(List<String> tools) {
        return new AgentWorktree(path, orchestratorRoot, outputStyle, disabledPlugins, tools, sandbox);
    }

    public AgentWorktree withSandbox(Sandbox sandbox) {
        return new AgentWorktree(path, orchestratorRoot, outputStyle, disabledPlugins, allowedTools, sandbox);
    }
}
