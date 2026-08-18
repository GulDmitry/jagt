package dev.jagt.orchestrator.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Where an agent's MCP client reaches jagt, and how it says WHICH task it is. Neither fact is agent-specific —
 * only the file that declares them is.
 *
 * <p>A runtime wires up exactly one of two paths:
 * <ul>
 *   <li><b>HTTP</b> — the CLI talks to {@link #url()} and carries {@link #CALLER_HEADER} itself, with nothing
 *       in between. Prefer this wherever the client supports a remote server.</li>
 *   <li><b>stdio</b> — the CLI can only SPAWN a server, so it spawns the standard bridge
 *       ({@code mcp_client.js}) which POSTs the same header. For clients whose config has no remote form.</li>
 * </ul>
 *
 * <p>The header is how a caller is scoped ({@code StateService.findByWorktree}), and jagt knows each worktree's
 * path when it writes the config — which is why the HTTP path can be static.
 */
@Component
public class McpEndpoint {

    /** Read by the backend on every MCP call to answer "which task is this?" — never a secret, just identity. */
    public static final String CALLER_HEADER = "X-Working-Directory";

    private final String url;

    public McpEndpoint(
            // Derived from the running port by default, so moving the backend does not silently point every
            // agent at a port nothing listens on.
            @Value("${orchestrator.mcp-url:http://127.0.0.1:${server.port:8290}/mcp}") String url) {
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
