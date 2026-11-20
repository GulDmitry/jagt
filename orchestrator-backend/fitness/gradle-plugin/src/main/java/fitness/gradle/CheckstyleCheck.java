package fitness.gradle;

import fitness.core.Measurement;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/** Counts the warnings the Checkstyle tasks reported, one item per report file holding any. */
public abstract class CheckstyleCheck extends FitnessFunctionCheck {

  @InputFiles
  @Optional
  @PathSensitive(PathSensitivity.NAME_ONLY)
  public abstract ConfigurableFileCollection getReports();

  @Override
  protected Measurement measure() {
    List<String> items = new ArrayList<>();
    for (File report : getReports().getFiles()) {
      if (report.isFile()) {
        items.addAll(Reports.warnings(report.toPath()));
      }
    }
    return Measurement.of(items);
  }

  /** Reads the warnings of one Checkstyle XML report. */
  static final class Reports {

    private Reports() {}

    static List<String> warnings(java.nio.file.Path report) {
      try {
        return Files.readAllLines(report).stream()
            .filter(line -> line.strip().startsWith("<error "))
            .map(String::strip)
            .toList();
      } catch (IOException e) {
        throw new IllegalStateException(report + " unreadable", e);
      }
    }
  }
}
