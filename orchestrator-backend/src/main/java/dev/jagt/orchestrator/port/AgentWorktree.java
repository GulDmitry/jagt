package dev.jagt.orchestrator.adapter.agent;

import java.nio.file.Path;
import java.util.List;

/**
 * What a runtime needs to provision one task's worktree. The two agent-shaped preferences are HINTS: a
 * runtime whose CLI has no notion of an output style or of plugins simply ignores them, which is why they are
 * passed as data rather than pushed into the interface as separate calls.
 *
 * @param path             the worktree the agent will run in
 * @param orchestratorRoot where {@code mcp_client.js} and the agent MCP configs are linked from
 * @param outputStyle      optional persona/output style to pin for the agent; null = the agent's own default
 * @param disabledPlugins  plugins the agent session should not load (heavy per-worktree language servers)
 */
public record AgentWorktree(Path path, Path orchestratorRoot, String outputStyle, List<String> disabledPlugins) {

    public AgentWorktree {
        disabledPlugins = disabledPlugins == null ? List.of() : List.copyOf(disabledPlugins);
    }
}
