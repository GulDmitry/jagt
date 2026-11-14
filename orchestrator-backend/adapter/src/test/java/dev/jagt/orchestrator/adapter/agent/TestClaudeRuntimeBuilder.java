package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.SessionBrief;
import java.time.Clock;
import java.util.List;
import lombok.With;

@With
public record TestClaudeRuntimeBuilder(String briefRoot, ClaudeProperties properties) {

  public static TestClaudeRuntimeBuilder claudeRuntime() {
    return new TestClaudeRuntimeBuilder(
        "/no-brief", new ClaudeProperties("claude", "", "", List.of()));
  }

  public ClaudeAgentRuntime build(Clock clock) {
    return new ClaudeAgentRuntime(
        new SessionBrief(
            new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(briefRoot))),
        properties,
        new McpEndpoint("http://localhost:8290/mcp"),
        new HookEndpoint(
            "http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"),
        clock);
  }
}
