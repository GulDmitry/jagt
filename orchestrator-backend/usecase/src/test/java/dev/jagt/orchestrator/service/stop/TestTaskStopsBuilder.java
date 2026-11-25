package dev.jagt.orchestrator.service.stop;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.log.StopLog;
import dev.jagt.orchestrator.testing.FakeHost;
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
            ConfigFile.defaults()::loopBreakerOrDefault,
            new StopAnnouncements(
                new StopLog(
                    new JsonMapper(),
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults()
                            .withRoot(root.toString())
                            .withData(root.toString()),
                        FakeHost.machine())))),
        state,
        clock,
        new FlowRules(new TaskChart(Composition.builtIn())));
  }
}
