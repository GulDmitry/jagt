package dev.jagt.orchestrator.mcp;

import tools.jackson.databind.JsonNode;

/** One MCP tool call. {@code callerTaskId} is the worktree the call came from, or null for the Master. */
@FunctionalInterface
public interface ToolHandler {
    String call(JsonNode args, String callerTaskId);
}
