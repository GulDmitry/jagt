package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandTimingGateTest {

  @TempDir Path dir;

  @Test
  void normalRunPassesWhenTheHistoryHoldsOneFastOutlierRightBeforeIt() throws IOException {
    Path history = dir.resolve("history.json");
    Files.writeString(
        history,
        """
        {"entries":{"Benchmark":[
          {"date":1,"benches":[{"name":"startup ubuntu-24.04/compose","value":12.0}]},
          {"date":2,"benches":[{"name":"startup ubuntu-24.04/compose","value":12.4}]},
          {"date":3,"benches":[{"name":"startup ubuntu-24.04/compose","value":11.8}]},
          {"date":4,"benches":[{"name":"startup ubuntu-24.04/compose","value":5.96}]}
        ]}}
        """);
    Path current = dir.resolve("timings.json");
    Files.writeString(
        current, "[{\"name\":\"startup ubuntu-24.04/compose\",\"unit\":\"s\",\"value\":12.6}]");

    List<String> violations = StandTimingGate.violations(history, current);

    assertThat(violations).isEmpty();
  }

  @Test
  void seriesMoreThanTwiceItsMedianFailsNamingOnlyThatSeries() throws IOException {
    Path history = dir.resolve("history.json");
    Files.writeString(
        history,
        """
        {"entries":{"Benchmark":[
          {"date":1,"benches":[
            {"name":"startup ubuntu-24.04/compose","value":10.0},
            {"name":"state p95 ubuntu-24.04/compose","value":20.0}]},
          {"date":2,"benches":[
            {"name":"startup ubuntu-24.04/compose","value":10.0},
            {"name":"state p95 ubuntu-24.04/compose","value":20.0}]},
          {"date":3,"benches":[
            {"name":"startup ubuntu-24.04/compose","value":10.0},
            {"name":"state p95 ubuntu-24.04/compose","value":20.0}]}
        ]}}
        """);
    Path current = dir.resolve("timings.json");
    Files.writeString(
        current,
        """
        [{"name":"startup ubuntu-24.04/compose","unit":"s","value":20.5},
         {"name":"state p95 ubuntu-24.04/compose","unit":"ms","value":21.0}]
        """);

    List<String> violations = StandTimingGate.violations(history, current);

    assertThat(violations).hasSize(1);
    assertThat(violations.get(0)).startsWith("startup ubuntu-24.04/compose");
  }

  @Test
  void everySeriesPassesWhenThereIsNoHistoryYet() throws IOException {
    Path history = dir.resolve("history.json");
    Files.writeString(history, "{\"entries\":{}}");
    Path current = dir.resolve("timings.json");
    Files.writeString(
        current, "[{\"name\":\"startup ubuntu-24.04/compose\",\"unit\":\"s\",\"value\":99.0}]");

    List<String> violations = StandTimingGate.violations(history, current);

    assertThat(violations).isEmpty();
  }

  @Test
  void everySeriesPassesWhenTheHistoryFileDoesNotExist() throws IOException {
    Path current = dir.resolve("timings.json");
    Files.writeString(
        current, "[{\"name\":\"startup ubuntu-24.04/compose\",\"unit\":\"s\",\"value\":99.0}]");

    List<String> violations = StandTimingGate.violations(dir.resolve("absent.json"), current);

    assertThat(violations).isEmpty();
  }
}
