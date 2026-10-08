package dev.jagt.orchestrator.port;

import java.util.List;
import java.util.Optional;

/** What the agent CLI says about its own MCP servers, free and token-less. */
public interface McpHealth {

    /**
     * The servers a read cannot use right now, each as {@code name (status)}. Empty {@code Optional} = could not be
     * established; an empty LIST means nothing is down.
     */
    Optional<List<String>> brokenServers();

    /** Every server the CLI lists, by name; empty {@code Optional} = could not be established. */
    Optional<List<String>> servers();
}
