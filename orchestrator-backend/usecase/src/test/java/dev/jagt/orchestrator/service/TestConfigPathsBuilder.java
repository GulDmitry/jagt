package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import java.nio.file.Path;
import lombok.With;

@With
public record TestConfigPathsBuilder(Path root) {

  public static TestConfigPathsBuilder configPaths() {
    return new TestConfigPathsBuilder(Path.of("/state"));
  }

  public OrchestratorPaths build() {
    return new OrchestratorPaths(
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString())
            .withConfigFile(root.resolve("jagt.yml").toString()));
  }
}
