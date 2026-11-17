package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandReportAppendTest {

  @TempDir Path directory;

  @AfterEach
  void clearReportProperty() {
    System.clearProperty("stand.report");
  }

  @Test
  void runsResultIsAppendedToTheReportFileNamedByTheProperty() throws Exception {
    Path report = directory.resolve("out/report.txt");
    System.setProperty("stand.report", report.toString());

    StandReport.append("happy-path/judge", "passed");
    StandReport.append("happy-path/judge", "failed");

    assertThat(Files.readAllLines(report))
        .containsExactly("happy-path/judge passed", "happy-path/judge failed");
  }

  @Test
  void nothingIsWrittenWhenNoReportIsNamed() {
    System.setProperty("stand.report", " ");

    StandReport.append("happy-path/judge", "passed");

    assertThat(directory).isEmptyDirectory();
  }

  @Test
  void reportThatCannotBeWrittenDoesNotFailTheRun() throws Exception {
    Path file = Files.writeString(directory.resolve("file"), "x");
    System.setProperty("stand.report", file.resolve("report.txt").toString());

    StandReport.append("happy-path/judge", "passed");

    assertThat(file).hasContent("x");
  }
}
