package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandReportAppendTest {

  @TempDir Path directory;

  @Test
  void runsResultIsAppendedToTheReportFileNamedByTheProperty() throws Exception {
    Path report = directory.resolve("out/report.txt");

    StandReport.append(report.toString(), "happy-path/judge", "passed");
    StandReport.append(report.toString(), "happy-path/judge", "failed");

    assertThat(Files.readAllLines(report))
        .containsExactly("happy-path/judge passed", "happy-path/judge failed");
  }

  @Test
  void nothingIsWrittenWhenNoReportIsNamed() {
    StandReport.append(" ", "happy-path/judge", "passed");

    assertThat(directory).isEmptyDirectory();
  }

  @Test
  void reportThatCannotBeWrittenDoesNotFailTheRun() throws Exception {
    Path file = Files.writeString(directory.resolve("file"), "x");

    StandReport.append(file.resolve("report.txt").toString(), "happy-path/judge", "passed");

    assertThat(file).hasContent("x");
  }
}
