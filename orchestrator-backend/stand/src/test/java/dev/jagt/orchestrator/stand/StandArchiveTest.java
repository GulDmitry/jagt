package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandArchiveTest {

  @TempDir Path directory;

  @AfterEach
  void clearReportProperty() {
    System.clearProperty("stand.report");
  }

  @Test
  void standardArchiveAppendsTheResultToTheReportNamedByTheProperty() throws Exception {
    Path report = directory.resolve("report.txt");
    System.setProperty("stand.report", report.toString());

    StandArchive.standard().report().accept("happy-path/judge", "passed");

    assertThat(Files.readAllLines(report)).containsExactly("happy-path/judge passed");
  }
}
