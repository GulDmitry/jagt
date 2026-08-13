package dev.jagt.orchestrator.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Where an agent's MCP client reaches jagt, and how it says WHICH task it is. Shared by every
 * {@link AgentRuntime}, because neither fact is agent-specific — only the file that declares them is (Claude's
 * {@code .mcp.json}, Codex's {@code config.toml}, the next CLI's whatever).
 *
 * <p>There are exactly two ways a runtime can wire this up, and picking one is the runtime's whole job here:
 * <ul>
 *   <li><b>HTTP</b> — the CLI talks to {@link #url()} directly and carries {@link #CALLER_HEADER} with the
 *       worktree path. Nothing runs in between: no proxy process, no second language on the machine. This is
 *       the path to prefer, and every MCP client that supports remote servers can take it.</li>
 *   <li><b>stdio</b> — the CLI can only SPAWN a server, so it spawns the standard bridge
 *       ({@code mcp_client.js}, linked by {@code AbstractAgentRuntime.linkStdioProxy}) which does the same POST
 *       with the same header. Kept for agents whose config has no remote-server form.</li>
 * </ul>
 *
 * <p>The header is what the backend scopes a caller by ({@code StateService.findByWorktree}), which is why the
 * HTTP path can be static config at all: jagt writes the config per worktree and already knows the path — the
 * proxy only ever computed it at runtime as {@code process.cwd()}.
 */
@Component
public class McpEndpoint {

    /** Read by the backend on every MCP call to answer "which task is this?" — never a secret, just identity. */
    public static final String CALLER_HEADER = "X-Working-Directory";

    private final String url;

    public McpEndpoint(
            // Derived from the running port by default, so moving the backend does not silently point every
            // agent at a port nothing listens on.
            @Value("${orchestrator.mcp-url:http://localhost:${server.port:8290}/mcp}") String url) {
        this.url = url;
    }

    public String url() {
        return url;
    }

    /** What {@link #CALLER_HEADER} must carry for this worktree: its absolute, symlink-free path. */
    public String callerHeaderValue(Path worktree) {
        return worktree.toAbsolutePath().normalize().toString();
    }
}
