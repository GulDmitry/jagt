package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.service.UsageTracker;
import java.time.Clock;
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
            runner, new AssistantBreaker(breaker, Clock.systemUTC(), new ReachLedger())),
        ClaudeProperties.defaults(),
        properties,
        usage,
        reads);
  }
}
