package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.port.Reach;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClaudeMcpReach implements Job {

  private static final String KEY = ClaudeMcpHealthProbe.DRIVER + "@" + ClaudeMcpHealthProbe.HOST;

  private final Reach reach;
  private final McpHealth health;

  @Override
  public String id() {
    return "claude-mcp-reach";
  }

  @Override
  public String describe() {
    return "asks the CLI again about its MCP servers while one is down, until all are connected";
  }

  @Override
  public Duration every() {
    return Duration.ofMinutes(1);
  }

  @Override
  public void run() {
    if (reach.standing().stream().anyMatch(lost -> KEY.equals(lost.key()))) {
      health.brokenServersNow();
    }
  }
}
