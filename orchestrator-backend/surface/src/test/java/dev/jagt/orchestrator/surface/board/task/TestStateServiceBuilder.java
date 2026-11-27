package dev.jagt.orchestrator.surface.board.task;

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
public record TestStateServiceBuilder(Path root) {

  public static TestStateServiceBuilder stateService() {
    return new TestStateServiceBuilder(Path.of("/state"));
  }

  public StateService build() {
    return new StateService(
        new JsonMapper(),
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()),
            FakeHost.machine()),
        new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {}, gap -> {}),
        event -> {});
  }
}
