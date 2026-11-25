package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import lombok.With;
import tools.jackson.databind.json.JsonMapper;

@With
public record TestStateServiceBuilder(OrchestratorProperties properties) {

  public static TestStateServiceBuilder stateService() {
    return new TestStateServiceBuilder(OrchestratorProperties.defaults());
  }

  public StateService build(JsonMapper mapper, Path root) {
    return new StateService(
        mapper,
        new OrchestratorPaths(
            properties
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()),
            FakeHost.machine()),
        new TaskReducer(new TaskChart(Composition.builtIn())),
        event -> {});
  }
}
