package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.config.ClaudeProperties;

import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import lombok.RequiredArgsConstructor;
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
 *
 * <p>Where the repository ships either of those two names, the briefing moves to {@code CLAUDE.local.md}
 * instead: it is loaded just the same, and it is the one name a project never versions.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.agent", havingValue = "claude", matchIfMissing = true)
@RequiredArgsConstructor
public class ClaudeAgentRuntime extends AbstractAgentRuntime {

    private static final String CLAUDE_MEMORY_FILE = "CLAUDE.md";
    private static final String LOCAL_MEMORY_FILE = "CLAUDE.local.md";

    private final OrchestratorProperties properties;
    private final ClaudeProperties claude;
    private final McpEndpoint mcp;

    @Override
    public String displayName() {
        return "Claude";
    }

    @Override
    public String launchCommand(Path worktree, boolean planMode) {
        return claude.command()
                + (planMode ? " --permission-mode plan" : "")
                + " " + shellQuote(properties.agentPrompt());
    }

    @Override
    public Path systemKnowledgeFile(Path worktree) {
        if (rootNamesFree(worktree)) {
            return worktree.resolve(SYSTEM_KNOWLEDGE_FILE);
        }
        Path local = worktree.resolve(LOCAL_MEMORY_FILE);
        if (broughtByCheckout(local)) {
            throw new IllegalStateException("Cannot brief the agent: the repository ships " + LOCAL_MEMORY_FILE
                    + " as well as its own memory file, so jagt has no name left to write.");
        }
        return local;
    }

    @Override
    protected void wireAgent(AgentWorktree worktree) {
        write(worktree.path().resolve(".mcp.json"), mcpJson(mcp.url(),
                mcp.callerHeaderValue(worktree.path())));
        if (rootNamesFree(worktree.path())) {
            symlink(worktree.path().resolve(CLAUDE_MEMORY_FILE),
                    worktree.path().resolve(SYSTEM_KNOWLEDGE_FILE));
        }
        write(worktree.path().resolve(".claude").resolve("settings.local.json"),
                settingsJson(worktree.outputStyle(), worktree.disabledPlugins()));
    }

    /**
     * Both names, because the pair is one layout: the alias is worthless when the file it points at cannot be
     * written, and writing that file is pointless when no name Claude reads can point at it.
     */
    private static boolean rootNamesFree(Path worktree) {
        return !broughtByCheckout(worktree.resolve(SYSTEM_KNOWLEDGE_FILE))
                && !broughtByCheckout(worktree.resolve(CLAUDE_MEMORY_FILE));
    }

    /**
     * The worktree's MCP config: Claude Code talks to the backend over HTTP and carries the caller header
     * itself, so there is no proxy process between them — which is what took Node out of jagt's requirements.
     * Written per worktree rather than symlinked from the root, because the header value IS the worktree path
     * (how the backend knows which task is calling).
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
