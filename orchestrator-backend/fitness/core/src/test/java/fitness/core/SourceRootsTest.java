package fitness.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceRootsTest {

  @Test
  void aTestSetIsEveryRootButMainInTheProjectAndItsModules(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("src/main/java"));
    Files.createDirectories(root.resolve("src/e2e/java"));
    Files.createDirectories(root.resolve("adapter/src/test/java"));
    Files.createDirectories(root.resolve("adapter/src/main/java"));

    assertThat(SourceRoots.of(new Project(root, "", List.of("all"), Map.of()), "test"))
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

    assertThat(SourceRoots.of(new Project(root, "", List.of("all"), Map.of()), "main,evalSupport"))
        .containsExactly(root.resolve("src/evalSupport/java"), root.resolve("src/main/java"));
  }

  @Test
  void aSetNamingNoRootIsRefused(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("src/main/java"));

    assertThatThrownBy(() -> SourceRoots.of(new Project(root, "", List.of("all"), Map.of()), "main,evalSuport"))
        .isInstanceOf(FitnessConfigException.class);
  }

  @Test
  void allSourceSetsSelectsEveryRootOfEverySourceSet(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("src/main/java"));
    Files.createDirectories(root.resolve("src/e2e/java"));
    Files.createDirectories(root.resolve("adapter/src/boardTest/java"));

    assertThat(SourceRoots.of(new Project(root, "", List.of("all"), Map.of()), "all"))
        .containsExactly(
            root.resolve("adapter/src/boardTest/java"),
            root.resolve("src/e2e/java"),
            root.resolve("src/main/java"));
  }

  @Test
  void aListedSourceSetLeavesOutTheRootsOfTheOthers(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("src/main/java"));
    Files.createDirectories(root.resolve("src/e2e/java"));

    assertThat(SourceRoots.of(new Project(root, "", List.of("main"), Map.of()), "all"))
        .containsExactly(root.resolve("src/main/java"));
  }

  @Test
  void aSetOutsideTheProjectsSourceSetsIsRefused(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("src/main/java"));
    Files.createDirectories(root.resolve("src/e2e/java"));

    assertThatThrownBy(() -> SourceRoots.of(new Project(root, "", List.of("main"), Map.of()), "e2e"))
        .isInstanceOf(FitnessConfigException.class);
  }
}
