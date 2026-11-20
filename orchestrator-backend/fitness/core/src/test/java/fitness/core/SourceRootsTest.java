package fitness.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceRootsTest {

  @Test
  void aTestSetIsEveryRootButMainInTheProjectAndItsModules(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("src/main/java"));
    Files.createDirectories(root.resolve("src/e2e/java"));
    Files.createDirectories(root.resolve("adapter/src/test/java"));
    Files.createDirectories(root.resolve("adapter/src/main/java"));

    assertThat(SourceRoots.of(root, "test"))
        .containsExactly(root.resolve("adapter/src/test/java"), root.resolve("src/e2e/java"));
  }

  @Test
  void aRootCompilesIntoItsModulesClassDirectory(@TempDir Path root) {
    assertThat(SourceRoots.classes(root, root.resolve("src/e2e/java")))
        .isEqualTo(root.resolve("build/classes/java/e2e"));
    assertThat(SourceRoots.classes(root, root.resolve("adapter/src/main/java")))
        .isEqualTo(root.resolve("adapter/build/classes/java/main"));
  }

  @Test
  void aNamedSetSelectsOnlyThatSourceSetsRoots(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("src/main/java"));
    Files.createDirectories(root.resolve("src/evalSupport/java"));
    Files.createDirectories(root.resolve("src/e2e/java"));

    assertThat(SourceRoots.of(root, "main,evalSupport"))
        .containsExactly(root.resolve("src/evalSupport/java"), root.resolve("src/main/java"));
  }

  @Test
  void aSetNamingNoRootIsRefused(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("src/main/java"));

    assertThatThrownBy(() -> SourceRoots.of(root, "main,evalSuport"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
