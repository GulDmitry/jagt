package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestConfigPathsBuilder.configPaths;

import dev.jagt.orchestrator.service.stop.BoardStop;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import lombok.With;

@With
public record TestBoardStopBuilder(Path root) {

  public static TestBoardStopBuilder boardStop() {
    return new TestBoardStopBuilder(Path.of("/state"));
  }

  public BoardStop build() {
    return new BoardStop(configPaths().withRoot(root).build(), new FakeClock());
  }
}
