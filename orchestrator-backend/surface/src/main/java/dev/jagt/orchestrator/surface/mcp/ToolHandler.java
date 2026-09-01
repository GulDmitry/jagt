package dev.jagt.orchestrator.surface.mcp;

import tools.jackson.databind.JsonNode;

/** {@code callerTaskId} is the worktree the call came from, or null for the Master. */
@FunctionalInterface
public interface ToolHandler {
    String call(JsonNode args, String callerTaskId);
}
