package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.With;

@With
public record TestSessionWakesBuilder(Path root, Instant now) {

  public static TestSessionWakesBuilder sessionWakes() {
    return new TestSessionWakesBuilder(Path.of("/state"), Instant.parse("2026-10-19T10:00:00Z"));
  }

  public SessionWakes build() {
    StateService state = stateService().withRoot(root).build();
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    return new SessionWakes(
        state,
        new TaskTurns(state, clock, new Checkers(new TaskChart(Composition.builtIn()))),
        clock);
  }
}
