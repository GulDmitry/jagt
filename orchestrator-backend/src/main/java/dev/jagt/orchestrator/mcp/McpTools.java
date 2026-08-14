package dev.jagt.orchestrator.mcp;

/**
 * A group of MCP tools that declares itself. The protocol class collects every implementation, so adding a
 * tool needs no edit there — and no single class ends up holding a collaborator per tool.
 */
public interface McpTools {
    void declare(McpToolRegistry tools);
}
