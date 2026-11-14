package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestSessionWakesBuilder.sessionWakes;

import dev.jagt.orchestrator.config.UsageLimitsProperties;
import dev.jagt.orchestrator.port.SubscriptionUsage;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.With;

@With
public record TestUsageLimitWakesBuilder(
    Path root, Instant now, double percent, Optional<Instant> resetsAt, boolean wakeOnReset) {

  public static TestUsageLimitWakesBuilder usageLimitWakes() {
    return new TestUsageLimitWakesBuilder(
        Path.of("/state"),
        Instant.parse("2026-10-19T10:00:00Z"),
        100,
        Optional.of(Instant.parse("2026-10-19T15:00:00Z")),
        true);
  }

  public UsageLimitWakes build() {
    SubscriptionUsage usage =
        () ->
            new SubscriptionUsage.Read(
                new SubscriptionUsage.Snapshot(
                    List.of(new SubscriptionUsage.Window("5h", percent, resetsAt)),
                    Optional.empty()));
    return new UsageLimitWakes(
        new UsageLimits(Optional.of(usage)),
        UsageLimitsProperties.defaults().withWakeOnReset(wakeOnReset),
        sessionWakes().withRoot(root).withNow(now).build(),
        Clock.fixed(now, ZoneOffset.UTC));
  }
}
