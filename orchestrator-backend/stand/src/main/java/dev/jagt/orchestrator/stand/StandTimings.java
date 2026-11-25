package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import tools.jackson.databind.json.JsonMapper;

/** Writes timings as a customSmallerIsBetter series, one per runner label and engine. */
public record StandTimings(String runner, String engine) {

  public static final Duration READY_BUDGET = Duration.ofSeconds(15);
  public static final Duration STATE_P95_BUDGET = Duration.ofMillis(100);

  private static final String STARTUP = "startup ";
  private static final String STATE_P95 = "state p95 ";

  public static OptionalDouble budgetOf(String series) {
    if (series.startsWith(STARTUP)) {
      return OptionalDouble.of(READY_BUDGET.toMillis() / 1000.0);
    }
    if (series.startsWith(STATE_P95)) {
      return OptionalDouble.of((double) STATE_P95_BUDGET.toMillis());
    }
    return OptionalDouble.empty();
  }

  public static StandTimings standard() {
    return new StandTimings(
        StandInputs.of("stand.runner", "local"), StandInputs.of("stand.engine", "compose"));
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
                    Map.of("name", STARTUP + series, "unit", "s", "value", startupSeconds),
                    Map.of("name", STATE_P95 + series, "unit", "ms", "value", stateP95Millis))));
  }
}
