package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.With;

@With
public record TestHeadlessClaudeBuilder(
    AssistantProperties breaker, AssistantProperties properties) {

  public static TestHeadlessClaudeBuilder headlessClaude() {
    return new TestHeadlessClaudeBuilder(AssistantProperties.empty(), AssistantProperties.empty());
  }

  public HeadlessClaude build(Processes runner, UsageTracker usage, ReadOnlyTools reads) {
    return new HeadlessClaude(
        new AssistantProcess(
            runner,
            new AssistantBreaker(
                breaker,
                Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC),
                new ReachLedger())),
        ClaudeProperties.defaults(),
        properties,
        usage,
        reads);
  }
}
