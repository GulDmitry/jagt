package dev.jagt.orchestrator.surface.mcp;

@FunctionalInterface
public interface McpToolRegistry {
    void tool(String name, String schemaJson, ToolHandler handler);
}
