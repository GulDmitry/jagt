package dev.jagt.orchestrator.port;

import lombok.With;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * What a runtime needs to provision one task's worktree. {@code outputStyle}, {@code disabledPlugins} and
 * {@code allowedTools} are HINTS a runtime whose CLI has no such notion ignores; null {@code outputStyle} = the
 * agent's own default.
 */
public record AgentWorktree(Path path, Path orchestratorRoot, @With @Nullable String outputStyle,
                            @With List<String> disabledPlugins, @With List<String> allowedTools, @With Sandbox sandbox) {

    public AgentWorktree {
        disabledPlugins = disabledPlugins == null ? List.of() : List.copyOf(disabledPlugins);
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    public static AgentWorktree of(Path path, Path orchestratorRoot) {
        return new AgentWorktree(path, orchestratorRoot, null, List.of(), List.of(), Sandbox.off());
    }
}
