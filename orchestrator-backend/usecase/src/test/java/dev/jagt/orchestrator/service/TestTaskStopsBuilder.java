package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.role.Arbiter;
import java.nio.file.Path;
import java.time.Clock;
import lombok.With;
import tools.jackson.databind.json.JsonMapper;

@With
public record TestTaskStopsBuilder(Path root, Arbiter.Profile profile) {

  public static TestTaskStopsBuilder taskStops() {
    return new TestTaskStopsBuilder(Path.of("/state"), Arbiter.Profile.OFF);
  }

  public TaskStops build(StateService state, Clock clock) {
    return new TaskStops(
        new StopPolicy(
            ignored -> profile,
            ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
            new StopAnnouncements(
                new StopLog(
                    new JsonMapper(),
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults().withRoot(root.toString()))))),
        state,
        clock,
        new FlowRules(new TaskChart(Composition.builtIn())));
  }
}
