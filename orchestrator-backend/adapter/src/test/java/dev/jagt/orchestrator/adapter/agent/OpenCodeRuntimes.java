package dev.jagt.orchestrator.adapter.agent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class OpenCodeRuntimes {

  private OpenCodeRuntimes() {}

  static OpenCodeAgentRuntime runtime(
      OpenCodeProperties properties, String mcpUrl, ConfigService config) {
    return new OpenCodeAgentRuntime(
        new SessionBrief(
            new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/no-brief"))),
        properties,
        new McpEndpoint(mcpUrl),
        config);
  }

  static OpenCodeAgentRuntime runtime(OpenCodeProperties properties, ConfigService config) {
    return runtime(properties, "http://127.0.0.1:8290/mcp", config);
  }

  static JsonNode provisioned(Path root, OpenCodeProperties properties, String mcpUrl)
      throws Exception {
    Path worktree = root.resolve("ABC-1-proj");
    worktree.toFile().mkdirs();
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());
    runtime(properties, mcpUrl, config).provisionWorktree(AgentWorktree.of(worktree, root));
    return new JsonMapper().readTree(worktree.resolve(".jagt/opencode/opencode.json").toFile());
  }
}
