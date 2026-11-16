package dev.jagt.orchestrator.adapter.assistant.reach;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * An MCP config file the container lacks is jagt's own reach failing, never a card's. A value
 * holding the JSON itself is not a file and is not read.
 */
@Component
@RequiredArgsConstructor
public class McpConfigReach implements Job {

  private final ClaudeProperties claude;
  private final AssistantProperties assistant;
  private final Reach reach;

  @Override
  public String id() {
    return "mcp-config-reach";
  }

  @Override
  public String describe() {
    return "reads whether each configured MCP config file is present and readable";
  }

  @Override
  public Duration every() {
    return Duration.ofMinutes(1);
  }

  @Override
  public void run() {
    read("claude.mcpConfig", claude.mcpConfig());
    read("assistant.mcpConfig", assistant.mcpConfig());
    assistant
        .mcpConfigByKind()
        .forEach((kind, file) -> read("assistant.mcpConfigByKind." + kind, file));
  }

  private void read(String key, String value) {
    String file = value.strip();
    if (file.isEmpty() || file.startsWith("{") || isReadable(file)) {
      reach.reached("mcpConfig", key);
      return;
    }
    reach.lost(
        new ReachLost(
            "mcpConfig",
            key,
            List.of(),
            key
                + " names '"
                + file
                + "', which is missing or unreadable here. Mount it (docs/server.md)."),
        () -> !isReadable(file));
  }

  private static boolean isReadable(String file) {
    Path path = Path.of(file);
    return Files.isRegularFile(path) && Files.isReadable(path);
  }
}
