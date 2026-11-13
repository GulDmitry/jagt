package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.ScenarioReach;
import java.time.Clock;
import lombok.With;

@With
public record TestTaskViewsBuilder(
    StateService stateService, ConfigService configService, ScenarioReach scenarios) {

  public static TestTaskViewsBuilder taskViews(
      StateService stateService, ConfigService configService) {
    return new TestTaskViewsBuilder(stateService, configService, () -> false);
  }

  public TaskViews build() {
    return new TaskViews(
        stateService,
        configService,
        new Rounds(
            configService,
            ignored -> false,
            scenarios,
            () -> false,
            new Checkers(new TaskChart(Composition.builtIn()))),
        Clock.systemUTC(),
        new FlowRules(new TaskChart(Composition.builtIn())));
  }
}
