package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NullMarkedTest {

  @ParameterizedTest
  @ValueSource(strings = {"core", "usecase", "adapter", "surface", "."})
  void everyPackageIsNonNullByDefault(String module) throws IOException {
    try (Stream<Path> sources = Files.walk(Path.of(module, "src/main/java"))) {
      assertThat(
              sources
                  .filter(file -> file.toString().endsWith(".java"))
                  .map(Path::getParent)
                  .distinct())
          .allSatisfy(
              pkg ->
                  assertThat(pkg.resolve("package-info.java")).content().contains("@NullMarked"));
    }
  }
}
