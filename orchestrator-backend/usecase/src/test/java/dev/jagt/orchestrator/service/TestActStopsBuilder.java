package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestBoardStopBuilder.boardStop;
import static dev.jagt.orchestrator.service.TestMasterBrakeBuilder.masterBrake;

import dev.jagt.orchestrator.service.master.ActStops;
import java.nio.file.Path;
import lombok.With;

@With
public record TestActStopsBuilder(Path root) {

  public static TestActStopsBuilder actStops() {
    return new TestActStopsBuilder(Path.of("/state"));
  }

  public ActStops build() {
    return new ActStops(masterBrake().withRoot(root).build(), boardStop().withRoot(root).build());
  }
}
