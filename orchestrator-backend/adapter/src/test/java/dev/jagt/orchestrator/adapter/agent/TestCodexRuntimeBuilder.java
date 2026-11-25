package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.adapter.agent.codex.CodexAgentRuntime;
import dev.jagt.orchestrator.adapter.agent.codex.CodexProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.testing.FakeHost;
import lombok.With;

@With
public record TestCodexRuntimeBuilder(String briefRoot, String orchestratorRoot, String mcpUrl) {

  public static TestCodexRuntimeBuilder codexRuntime() {
    return new TestCodexRuntimeBuilder(
        "/no-brief", "/orchestrator-root", "http://127.0.0.1:8290/mcp");
  }

  public CodexAgentRuntime build() {
    return new CodexAgentRuntime(
        new SessionBrief(
            new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(briefRoot), FakeHost.machine())),
        CodexProperties.defaults(),
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(orchestratorRoot), FakeHost.machine()),
        new McpEndpoint(mcpUrl));
  }
}
