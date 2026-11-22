package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Names each series of the current timings that passes both twice the median of its last ten
 * history entries and its reference-machine budget.
 */
public final class StandTimingGate {

  private static final int WINDOW = 10;
  private static final double FACTOR = 2.0;

  private StandTimingGate() {}

  public static void main(String[] args) throws IOException {
    List<String> violations = violations(Path.of(args[0]), Path.of(args[1]));
    if (!violations.isEmpty()) {
      violations.forEach(StandConsole::line);
      throw new IllegalStateException(
          "stand timing over " + FACTOR + "x its median: " + violations);
    }
    StandConsole.line("stand timing gate: every series within " + FACTOR + "x its median");
  }

  static List<String> violations(Path history, Path current) throws IOException {
    if (!Files.exists(history)) {
      return List.of();
    }
    JsonMapper mapper = JsonMapper.builder().build();
    JsonNode entries = mapper.readTree(Files.readString(history)).path("entries");
    List<String> violations = new ArrayList<>();
    for (JsonNode bench : mapper.readTree(Files.readString(current))) {
      String name = bench.get("name").asString();
      double value = bench.get("value").asDouble();
      List<Double> recent = recent(entries, name);
      if (!recent.isEmpty()
          && value > FACTOR * median(recent)
          && value > StandTimings.budgetOf(name).orElse(0)) {
        violations.add(name + ": " + value + " over " + FACTOR + "x median " + median(recent));
      }
    }
    return violations;
  }

  private static List<Double> recent(JsonNode entries, String name) {
    List<JsonNode> runs = new ArrayList<>();
    for (Map.Entry<String, JsonNode> suite : entries.properties()) {
      suite.getValue().forEach(runs::add);
    }
    runs.sort(Comparator.comparingLong(run -> run.path("date").asLong()));
    List<Double> values = new ArrayList<>();
    for (JsonNode run : runs) {
      for (JsonNode bench : run.path("benches")) {
        if (name.equals(bench.path("name").asString())) {
          values.add(bench.path("value").asDouble());
        }
      }
    }
    return values.subList(Math.max(0, values.size() - WINDOW), values.size());
  }

  private static double median(List<Double> values) {
    List<Double> sorted = values.stream().sorted().toList();
    int middle = sorted.size() / 2;
    return sorted.size() % 2 == 1
        ? sorted.get(middle)
        : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
  }
}
