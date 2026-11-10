package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.port.AgentWorktree;
import java.nio.file.Path;

final class CodexRuntimes {

  private CodexRuntimes() {}

  private static CodexAgentRuntime runtime(String briefRoot, String orchestratorRoot, String url) {
    return new CodexAgentRuntime(
        new SessionBrief(
            new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(briefRoot))),
        CodexProperties.defaults(),
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(orchestratorRoot)),
        new McpEndpoint(url));
  }

  static CodexAgentRuntime runtime() {
    return runtime("/no-brief", "/orchestrator-root", "http://127.0.0.1:8290/mcp");
  }

  static CodexAgentRuntime runtimeBriefedBy(Path root) {
    return runtime(root.toString(), "/orchestrator-root", "http://127.0.0.1:8290/mcp");
  }

  static CodexAgentRuntime runtimeBundledAt(Path orchestratorRoot) {
    return runtime("/no-brief", orchestratorRoot.toString(), "http://127.0.0.1:8290/mcp");
  }

  static CodexAgentRuntime runtimeOn(String mcpUrl) {
    return runtime("/no-brief", "/orchestrator-root", mcpUrl);
  }

  static Path provisionedWorktree(Path root, CodexAgentRuntime runtime) {
    Path worktree = root.resolve("ABC-1-proj");
    worktree.toFile().mkdirs();
    runtime.provisionWorktree(AgentWorktree.of(worktree, root));
    return worktree;
  }
}
