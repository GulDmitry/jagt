package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Writes timings as a customSmallerIsBetter series, one per runner label and engine. */
public record StandTimings(String runner, String engine) {

  public static StandTimings standard() {
    return new StandTimings(
        System.getProperty("stand.runner", "local"), System.getProperty("stand.engine", "compose"));
  }

  public void write(Path file, double startupSeconds, double stateP95Millis) throws IOException {
    String series = runner + "/" + engine;
    Files.createDirectories(file.toAbsolutePath().getParent());
    Files.writeString(
        file,
        JsonMapper.builder()
            .build()
            .writeValueAsString(
                List.of(
                    Map.of("name", "startup " + series, "unit", "s", "value", startupSeconds),
                    Map.of("name", "state p95 " + series, "unit", "ms", "value", stateP95Millis))));
  }
}
