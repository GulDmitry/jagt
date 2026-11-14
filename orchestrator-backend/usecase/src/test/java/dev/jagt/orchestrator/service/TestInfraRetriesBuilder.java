package dev.jagt.orchestrator.service;

import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.With;

@With
public record TestInfraRetriesBuilder(Instant now) {

  public static TestInfraRetriesBuilder infraRetries() {
    return new TestInfraRetriesBuilder(Instant.parse("2026-10-29T10:00:00Z"));
  }

  public InfraRetries build(StateService state) {
    return new InfraRetries(
        state,
        mock(TaskStops.class),
        InfraWaitProperties.defaults(),
        Clock.fixed(now, ZoneOffset.UTC));
  }
}
