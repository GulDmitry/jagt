package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PinningTest {

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {".", "build-logic", "statechart", "core", "usecase", "adapter", "surface"})
  void projectDependencyChangeShowsInItsLockfile(String project) {
    assertThat(Path.of(project, "gradle.lockfile")).isRegularFile();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {".", "build-logic"})
  void pluginChangeShowsInItsBuildscriptLockfile(String build) {
    assertThat(Path.of(build, "buildscript-gradle.lockfile")).isRegularFile();
  }

  @Test
  void everyIncludedModuleHoldsLockfile() {
    List<String> modules =
        Pattern.compile("^include '([^']+)'", Pattern.MULTILINE)
            .matcher(SourceRoots.read(Path.of("settings.gradle")))
            .results()
            .map(m -> m.group(1))
            .toList();

    assertThat(modules).isNotEmpty();
    assertThat(modules).allSatisfy(m -> assertThat(Path.of(m, "gradle.lockfile")).isRegularFile());
  }

  @Test
  void wrapperChangeShowsInItsChecksum() {
    assertThat(SourceRoots.read(Path.of("gradle/wrapper/gradle-wrapper.properties")))
        .containsPattern("(?m)^distributionSha256Sum=[0-9a-f]{64}$");
  }

  @Test
  void everyWorkflowActionIsPinnedToCommitWithItsTag() {
    List<String> unpinned =
        SourceRoots.read(Path.of("../.github/workflows/ci.yml"))
            .lines()
            .filter(line -> line.contains("uses:"))
            .filter(line -> !line.matches(".*uses: \\S+@[0-9a-f]{40} # \\S+$"))
            .toList();

    assertThat(unpinned).isEmpty();
  }

  @Test
  void everyGitlabImageIsPinnedByDigest() {
    List<String> unpinned =
        SourceRoots.read(Path.of("../.gitlab-ci.yml"))
            .lines()
            .filter(line -> line.matches("\\s*(image:|- docker:).*"))
            .filter(line -> !line.matches(".*\\S+:\\S+@sha256:[0-9a-f]{64}$"))
            .toList();

    assertThat(unpinned).isNotNull().isEmpty();
  }

  @Test
  void everyDockerfileBaseIsPinnedByTagAndDigestUnlessItIsAnArgument() throws IOException {
    List<String> unpinned;
    try (Stream<Path> files = Files.walk(Path.of(".."), 6)) {
      unpinned =
          files
              .filter(p -> p.getFileName().toString().startsWith("Dockerfile"))
              .filter(p -> !p.toString().matches(".*/(\\.claude|build|node_modules|\\.git)/.*"))
              .flatMap(p -> SourceRoots.read(p).lines().map(line -> p + ": " + line))
              .filter(line -> line.matches(".*: FROM .*"))
              .filter(line -> !line.contains("${"))
              .filter(line -> !line.matches(".*: FROM \\S+:\\S+@sha256:[0-9a-f]{64}( AS \\S+)?$"))
              .toList();
    }

    assertThat(unpinned).isEmpty();
  }

  @Test
  void pinMovesByReviewedUpdateRequest() {
    assertThat(SourceRoots.read(Path.of("../renovate.json"))).contains("config:recommended");
  }
}
