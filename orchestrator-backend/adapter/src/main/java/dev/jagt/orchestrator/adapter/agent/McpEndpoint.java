package dev.jagt.orchestrator.adapter.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Where an agent's MCP client reaches jagt, and how it says WHICH task it is. A client supporting a remote
 * server carries {@link #CALLER_HEADER} itself; one that can only SPAWN a server gets the bridge, which POSTs
 * the same header. The endpoint can be static because the header, not the address, carries the worktree.
 */
@Component
public class McpEndpoint {

    /** Identity, never a secret: which task an MCP call comes from. */
    public static final String CALLER_HEADER = "X-Working-Directory";

    private final String url;

    public McpEndpoint(
            // Derived from the running port, so moving the backend cannot point every agent at a dead one.
            @Value("${orchestrator.mcp-url:http://127.0.0.1:${server.port:8290}/mcp}") String url) {
        this.url = url;
    }

    public String url() {
        return url;
    }

    public static String callerHeaderValue(Path worktree) {
        return worktree.toAbsolutePath().normalize().toString();
    }
}
