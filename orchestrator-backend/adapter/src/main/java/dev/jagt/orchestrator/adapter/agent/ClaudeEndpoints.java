package dev.jagt.orchestrator.adapter.agent;

import org.springframework.stereotype.Component;

@Component
public record ClaudeEndpoints(McpEndpoint mcp, HookEndpoint hooks) {}
