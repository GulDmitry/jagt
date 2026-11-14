package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.With;

@With
public record TestTaskViewsBuilder(boolean scenariosReady) {

  public static TestTaskViewsBuilder defaults() {
    return new TestTaskViewsBuilder(false);
  }

  public TaskViews build(StateService stateService, ConfigService configService) {
    return new TaskViews(
        stateService,
        configService,
        new Rounds(
            configService,
            ignored -> false,
            () -> scenariosReady,
            () -> false,
            new Checkers(new TaskChart(Composition.builtIn()))),
        Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC),
        new FlowRules(new TaskChart(Composition.builtIn())));
  }
}
