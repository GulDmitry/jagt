package dev.jagt.orchestrator.agent;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Claude Code runtime — the default agent. Selected by {@code orchestrator.agent=claude}.
 *
 * <p>Its worktree gets the project MCP config ({@code .mcp.json}, symlinked from the orchestrator root),
 * a generated {@code .claude/settings.local.json} that pre-approves what an unwatched session must not stop
 * on, and {@code CLAUDE.md} as a symlink to the shared {@link AgentRuntime#SYSTEM_KNOWLEDGE_FILE} — Claude
 * reads its own filename, jagt keeps writing one file.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.agent", havingValue = "claude", matchIfMissing = true)
public class ClaudeAgentRuntime extends AbstractAgentRuntime {

    private final OrchestratorProperties properties;
    private final McpEndpoint mcp;

    public ClaudeAgentRuntime(OrchestratorProperties properties, McpEndpoint mcp) {
        this.properties = properties;
        this.mcp = mcp;
    }

    @Override
    public String displayName() {
        return "Claude";
    }

    @Override
    public String launchCommand(Path worktree, boolean planMode) {
        return properties.claudeCommand()
                + (planMode ? " --permission-mode plan" : "")
                + " " + shellQuote(properties.agentPrompt());
    }

    @Override
    protected void wireAgent(AgentWorktree worktree) {
        write(worktree.path().resolve(".mcp.json"), mcpJson(mcp.url(),
                mcp.callerHeaderValue(worktree.path())));
        symlink(worktree.path().resolve("CLAUDE.md"), worktree.path().resolve(SYSTEM_KNOWLEDGE_FILE));
        write(worktree.path().resolve(".claude").resolve("settings.local.json"),
                settingsJson(worktree.outputStyle(), worktree.disabledPlugins()));
    }

    /**
     * The worktree's MCP config: Claude Code talks to the backend over HTTP and carries the caller header
     * itself, so there is no proxy process between them — which is what took Node out of jagt's requirements.
     * Written per worktree rather than symlinked from the root, because the header value IS the worktree path
     * (that is how the backend knows which task is calling; the old bridge computed it as {@code process.cwd()}).
     */
    static String mcpJson(String url, String worktreePath) {
        return """
                {
                  "mcpServers": {
                    "jagt-orchestrator": {
                      "type": "http",
                      "url": "%s",
                      "headers": { "%s": "%s" }
                    }
                  }
                }
                """.formatted(url, McpEndpoint.CALLER_HEADER, worktreePath.replace("\\", "\\\\"));
    }

    /**
     * The generated worktree {@code .claude/settings.local.json}. Without {@code enableAllProjectMcpServers}
     * every spawned session stops at a "New MCP server found" prompt; without the allow-list Claude's
     * auto-mode classifier still gates individual calls, freezing the agent on invisible prompts nobody in the
     * tmux window answers — MCP calls (even notify_user) and the agent's own git commit/push on ship. The
     * output style is pinned here because a worktree is an untrusted project where the human's global style
     * may not apply; disabled plugins keep a ~1-2GB language server per worktree from spawning when the human
     * opted into that. Valid JSON in all cases.
     */
    static String settingsJson(String outputStyle, List<String> disabledPlugins) {
        String styleLine = outputStyle == null || outputStyle.isBlank() ? ""
                : "\n  \"outputStyle\": \"" + outputStyle.replace("\\", "\\\\").replace("\"", "\\\"") + "\",";
        String pluginsLine = "";
        if (disabledPlugins != null) {
            String entries = disabledPlugins.stream()
                    .filter(p -> p != null && !p.isBlank())
                    .map(p -> "\"" + p.strip() + "\": false")
                    .collect(Collectors.joining(", "));
            if (!entries.isBlank()) {
                pluginsLine = "\n  \"enabledPlugins\": {" + entries + "},";
            }
        }
        return """
                {%s%s
                  "enableAllProjectMcpServers": true,
                  "permissions": {
                    "allow": ["mcp__jagt-orchestrator", "Bash(git:*)"]
                  }
                }
                """.formatted(styleLine, pluginsLine);
    }
}
