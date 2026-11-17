package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.StateEvents;
import java.nio.file.Path;
import lombok.With;
import tools.jackson.databind.json.JsonMapper;

@With
public record TestStateServiceBuilder(Path root) {

  public static TestStateServiceBuilder stateService() {
    return new TestStateServiceBuilder(Path.of("/state"));
  }

  public StateService build() {
    return build(event -> {});
  }

  public StateService build(StateEvents events) {
    return new StateService(
        new JsonMapper(),
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withData(root.toString())
                .withStateFile(root.resolve("state.json").toString())),
        new TaskReducer(new TaskChart(Composition.builtIn())),
        events);
  }
}
