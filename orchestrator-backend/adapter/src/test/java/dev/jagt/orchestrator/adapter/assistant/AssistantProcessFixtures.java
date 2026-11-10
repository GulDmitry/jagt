package dev.jagt.orchestrator.adapter.assistant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ReachLedger;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

final class AssistantProcessFixtures {

  private AssistantProcessFixtures() {}

  static AssistantProcess openedThenCooledDown(
      ProcessRunner runner, Clock clock, Processes.Result failure) {
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(0L));
    AssistantProcess probing =
        new AssistantProcess(
            runner,
            new AssistantBreaker(
                AssistantProperties.empty()
                    .withBreakerFailures(3)
                    .withBreakerCooldown(Duration.ofMinutes(5)),
                clock,
                new ReachLedger()));
    when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(failure);
    probing.run(Path.of("."), Duration.ofMinutes(1), List.of("claude"));
    probing.run(Path.of("."), Duration.ofMinutes(1), List.of("claude"));
    probing.run(Path.of("."), Duration.ofMinutes(1), List.of("claude"));
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(300_001L));
    return probing;
  }
}
