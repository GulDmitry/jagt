package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestConfigPathsBuilder.configPaths;

import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.testing.FakeConfigInputs;
import java.nio.file.Path;
import lombok.With;

@With
public record TestConfigSourceBuilder(Path root) {

  public static TestConfigSourceBuilder configSource() {
    return new TestConfigSourceBuilder(Path.of("/state"));
  }

  public ConfigSource build() {
    return new ConfigSource(configPaths().withRoot(root).build(), new FakeConfigInputs());
  }
}
