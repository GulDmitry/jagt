package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import lombok.With;

@With
public record TestOrchestratorPathsBuilder(Path root) {

  public static TestOrchestratorPathsBuilder orchestratorPaths() {
    return new TestOrchestratorPathsBuilder(Path.of("/state"));
  }

  public OrchestratorPaths build() {
    return new OrchestratorPaths(
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withData(root.toString())
            .withStateFile(root.resolve("state.json").toString()),
        FakeHost.machine());
  }
}
