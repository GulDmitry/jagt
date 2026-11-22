package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StandTimingsTest {

  @TempDir Path dir;

  @Test
  void recordsStartupAndStateP95AsSeparateSeriesKeyedByRunnerAndEngine() throws IOException {
    Path file = dir.resolve("nested/timings.json");

    new StandTimings("ubuntu-24.04", "compose").write(file, 4.5, 12.25);

    JsonNode timings = JsonMapper.builder().build().readTree(Files.readString(file));
    assertThat(timings).hasSize(2);
    assertThat(timings.get(0).get("name").asString()).isEqualTo("startup ubuntu-24.04/compose");
    assertThat(timings.get(0).get("unit").asString()).isEqualTo("s");
    assertThat(timings.get(0).get("value").asDouble()).isEqualTo(4.5);
    assertThat(timings.get(1).get("name").asString()).isEqualTo("state p95 ubuntu-24.04/compose");
    assertThat(timings.get(1).get("unit").asString()).isEqualTo("ms");
    assertThat(timings.get(1).get("value").asDouble()).isEqualTo(12.25);
  }
}
