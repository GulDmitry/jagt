package dev.jagt.orchestrator.adapter.assistant.reach;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.agent.McpHealth;
import dev.jagt.orchestrator.port.reach.Reach;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClaudeMcpReach implements Job {

  private static final String KEY = ClaudeMcpHealthProbe.DRIVER + "@" + ClaudeMcpHealthProbe.HOST;

  /** Each probe starts every configured server. */
  private static final Duration WATCH_EVERY = Duration.ofMinutes(10);

  private final Reach reach;
  private final McpHealth health;
  private final Clock clock;

  private Instant watchedAt = Instant.EPOCH;

  @Override
  public String id() {
    return "claude-mcp-reach";
  }

  @Override
  public String describe() {
    return "asks the CLI about its MCP servers: every minute while one is down, else every ten";
  }

  @Override
  public Duration every() {
    return Duration.ofMinutes(1);
  }

  @Override
  public void run() {
    if (reach.standing().stream().anyMatch(lost -> KEY.equals(lost.key()))) {
      health.brokenServersNow();
      watchedAt = clock.instant();
    } else if (!clock.instant().isBefore(watchedAt.plus(WATCH_EVERY))) {
      health.brokenServers();
      watchedAt = clock.instant();
    }
  }
}
