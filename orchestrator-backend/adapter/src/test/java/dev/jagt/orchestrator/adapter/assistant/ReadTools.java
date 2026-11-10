package dev.jagt.orchestrator.adapter.assistant;

import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.adapter.agent.HookEndpoint;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.service.ReadScopes;
import dev.jagt.orchestrator.task.AssistantCallKind;
import java.util.List;

final class ReadTools {

  private ReadTools() {}

  static List<String> allowedOnPinnedGitlab() {
    var pinned =
        AssistantProperties.empty()
            .withMcpConfig("{\"mcpServers\":{\"gitlab\":{\"command\":\"x\"}}}");
    return new ReadOnlyTools(
            mock(McpHealth.class), pinned, new ReadScopes(), mock(HookEndpoint.class))
        .allowed(AssistantCallKind.MR_READ);
  }
}
