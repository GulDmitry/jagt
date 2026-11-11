package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.HookEndpoint;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.service.ReadScopes;
import java.nio.file.Path;
import lombok.With;

@With
public record TestOpenCodeGateBuilder(Path state) {

  public static TestOpenCodeGateBuilder openCodeGate() {
    return new TestOpenCodeGateBuilder(Path.of("/state"));
  }

  public OpenCodeGate build(McpHealth health, ReadScopes scopes) {
    return new OpenCodeGate(
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(state.toString())),
        new ReadOnlyTools(
            health,
            AssistantProperties.empty(),
            scopes,
            new HookEndpoint(
                "http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent")),
        new HookEndpoint(
            "http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"));
  }
}
