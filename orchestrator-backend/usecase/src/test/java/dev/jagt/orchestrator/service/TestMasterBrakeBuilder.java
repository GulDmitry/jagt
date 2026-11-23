package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestConfigPathsBuilder.configPaths;

import dev.jagt.orchestrator.service.master.MasterBrake;
import java.nio.file.Path;
import lombok.With;

@With
public record TestMasterBrakeBuilder(Path root) {

  public static TestMasterBrakeBuilder masterBrake() {
    return new TestMasterBrakeBuilder(Path.of("/state"));
  }

  public MasterBrake build() {
    return new MasterBrake(configPaths().withRoot(root).build());
  }
}
