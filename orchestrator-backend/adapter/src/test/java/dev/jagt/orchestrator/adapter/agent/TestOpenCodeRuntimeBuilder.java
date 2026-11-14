package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.service.ConfigService;
import lombok.With;

@With
public record TestOpenCodeRuntimeBuilder(
    String briefRoot, OpenCodeProperties properties, String mcpUrl) {

  public static TestOpenCodeRuntimeBuilder openCodeRuntime() {
    return new TestOpenCodeRuntimeBuilder(
        "/no-brief", OpenCodeProperties.defaults(), "http://127.0.0.1:8290/mcp");
  }

  public OpenCodeAgentRuntime build(ConfigService config) {
    return new OpenCodeAgentRuntime(
        new SessionBrief(
            new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(briefRoot))),
        properties,
        new McpEndpoint(mcpUrl),
        config);
  }
}
