package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandRunLogTest {

  @Test
  void failedRunKeepsItsViolationsAndAnUnknownSpend(@TempDir Path dir) throws Exception {
    StandRunLog.keep(
        dir,
        new StandRunLog.Entry(
            Instant.ofEpochMilli(1000),
            "happy-path",
            "act",
            "live",
            new StandRunLog.Verdict(
                false, "timed out", "", List.of("NoForcePush at 2026-10-24T10:00:00Z: pushed -f")),
            93,
            new StandSpend("unknown: no usage file", Map.of())),
        "jagt-stand-1-1");

    List<Path> kept;
    try (Stream<Path> stream = Files.list(dir)) {
      kept = stream.toList();
    }

    assertThat(kept)
        .singleElement()
        .satisfies(
            file ->
                assertThat(Files.readString(file))
                    .contains(
                        "\"passed\" : false",
                        "NoForcePush",
                        "\"wallSeconds\" : 93",
                        "unknown: no usage file",
                        "\"source\" : \"live\""));
  }

  @Test
  void theOldestRunsFoldAwayPastTheCeiling(@TempDir Path dir) throws Exception {
    IntStream.rangeClosed(1, StandRunLog.KEPT + 1)
        .forEach(
            n ->
                StandRunLog.keep(
                    dir,
                    new StandRunLog.Entry(
                        Instant.ofEpochMilli(n),
                        "plan",
                        "judge",
                        "live",
                        new StandRunLog.Verdict(true, "reached", "", List.of()),
                        1,
                        new StandSpend("unknown: no usage file", Map.of())),
                    "jagt-stand-1-" + n));

    List<String> kept;
    try (Stream<Path> stream = Files.list(dir)) {
      kept = stream.map(file -> file.getFileName().toString()).sorted().toList();
    }

    assertThat(kept).hasSize(StandRunLog.KEPT).first().asString().startsWith("0000000000002-");
  }
}
