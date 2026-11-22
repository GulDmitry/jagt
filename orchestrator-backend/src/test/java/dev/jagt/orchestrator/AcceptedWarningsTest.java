package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcceptedWarningsTest {

  private static final Path BUILD_LOGIC = Path.of("build-logic");

  @Test
  void everyRegisteredWarningNamesItsPatternItsSourceAndWhatEndsIt() throws IOException {
    List<List<String>> rows =
        Files.readAllLines(BUILD_LOGIC.resolve("accepted-warnings.txt")).stream()
            .filter(line -> !line.isBlank() && !line.startsWith("#"))
            .map(line -> Arrays.stream(line.split("\\|", -1)).map(String::strip).toList())
            .toList();

    assertThat(rows)
        .allSatisfy(row -> {
          assertThat(row).hasSize(3);
          assertThat(row).allMatch(field -> !field.isEmpty());
          assertThat(row.get(1)).contains(":");
        });
  }

  @Test
  void everyRegisteredSourceIsTheVersionStillInUse() throws IOException {
    String declared =
        Files.readString(BUILD_LOGIC.resolve("build.gradle"))
            + Files.readString(Path.of("gradle/wrapper/gradle-wrapper.properties"))
                .replace("gradle-", "gradle:");
    List<String> sources =
        Files.readAllLines(BUILD_LOGIC.resolve("accepted-warnings.txt")).stream()
            .filter(line -> !line.isBlank() && !line.startsWith("#"))
            .map(line -> line.split("\\|", -1)[1].strip())
            .toList();

    assertThat(sources).allMatch(declared::contains);
  }
}
