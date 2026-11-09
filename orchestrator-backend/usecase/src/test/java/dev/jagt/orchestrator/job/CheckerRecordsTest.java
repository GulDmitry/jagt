package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.AcceptanceEnding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckerRecordsTest {

  @Test
  void readsTheVerdictsAnOlderVersionKeptUnderTheQaNameAndWritesThemUnderTheNewOne(
      @TempDir Path root) throws Exception {
    Files.writeString(root.resolve("qa-verdicts"), "ABC-42\td1\tabc123\tNOT_MET\t7\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    CheckerRecords records =
        new CheckerRecords(paths, Clock.fixed(Instant.ofEpochMilli(9), ZoneOffset.UTC));

    AcceptanceEnding ending = records.latest("ABC-42").orElseThrow().ending();
    records.put("ABC-43", "d2", "def456", AcceptanceEnding.PASSED);

    assertThat(ending).isEqualTo(AcceptanceEnding.NOT_MET);
    assertThat(Files.readAllLines(root.resolve("acceptance-verdicts")))
        .hasSize(2)
        .first()
        .asString()
        .startsWith("ABC-42\td1\tabc123\tNOT_MET");
  }
}
