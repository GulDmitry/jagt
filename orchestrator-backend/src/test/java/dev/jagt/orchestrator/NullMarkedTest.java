package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NullMarkedTest {

  @Test
  void everyPackageIsNonNullByDefault() {
    assertThat(SourceRoots.javaFiles().map(Path::getParent).distinct())
        .allSatisfy(
            pkg -> assertThat(pkg.resolve("package-info.java")).content().contains("@NullMarked"));
  }
}
