package dev.jagt.orchestrator.mcp;

/** Where a group declares its tools. Schema and handler arrive together, so neither can drift. */
@FunctionalInterface
public interface McpToolRegistry {
    void tool(String name, String schemaJson, ToolHandler handler);
}
