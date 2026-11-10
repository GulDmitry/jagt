package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.port.AgentWorktree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class ClaudeRuntimes {

  private ClaudeRuntimes() {}

  static ClaudeProperties defaultProperties() {
    return new ClaudeProperties("claude", "", "", List.of());
  }

  static ClaudeAgentRuntime runtime() {
    return runtimeBriefedBy("/no-brief", defaultProperties(), Clock.systemUTC());
  }

  static ClaudeAgentRuntime runtime(ClaudeProperties properties) {
    return runtimeBriefedBy("/no-brief", properties, Clock.systemUTC());
  }

  static ClaudeAgentRuntime runtime(Clock clock) {
    return runtimeBriefedBy("/no-brief", defaultProperties(), clock);
  }

  static ClaudeAgentRuntime runtimeBriefedBy(Path root) {
    return runtimeBriefedBy(root.toString(), defaultProperties(), Clock.systemUTC());
  }

  static ClaudeAgentRuntime runtimeBriefedBy(Path root, ClaudeProperties properties) {
    return runtimeBriefedBy(root.toString(), properties, Clock.systemUTC());
  }

  private static ClaudeAgentRuntime runtimeBriefedBy(
      String root, ClaudeProperties properties, Clock clock) {
    return new ClaudeAgentRuntime(
        new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root))),
        properties,
        new McpEndpoint("http://localhost:8290/mcp"),
        new HookEndpoint(
            "http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"),
        clock);
  }

  static void writeBrief(Path root, String text) throws IOException {
    Files.createDirectories(root.resolve("roles/session"));
    Files.writeString(root.resolve("roles/session/brief.md"), text);
  }

  static Path provisionedWorktree(Path root) throws IOException {
    Path worktree = root.resolve("ABC-1-proj");
    worktree.toFile().mkdirs();
    runtime().provisionWorktree(AgentWorktree.of(worktree, root));
    return worktree;
  }

  static JsonNode settingsOf(Path worktree) throws IOException {
    return new JsonMapper()
        .readTree(Files.readString(worktree.resolve(".claude/settings.local.json")));
  }

  static List<String> textsOf(JsonNode array) {
    List<String> texts = new ArrayList<>();
    array.forEach(n -> texts.add(n.asString("")));
    return texts;
  }

  static Path transcriptOf(Path worktree, String content) throws IOException {
    return Files.writeString(
        Files.createDirectories(
                ClaudeTranscripts.projectsDir().resolve(ClaudeTranscripts.slug(worktree)))
            .resolve("s.jsonl"),
        content);
  }
}
