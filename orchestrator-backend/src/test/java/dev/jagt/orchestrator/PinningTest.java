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
  void dependencyChecksumChangeFailsTheBuildBecauseEveryLockedModuleIsVerified()
      throws IOException {
    String metadata = SourceRoots.read(Path.of("gradle/verification-metadata.xml"));
    List<String> unverified;
    try (Stream<Path> files = Files.walk(Path.of("."), 4)) {
      unverified =
          files
              .filter(p -> p.getFileName().toString().endsWith("gradle.lockfile"))
              .filter(p -> !p.toString().matches(".*/(build|\\.gradle|node_modules)/.*"))
              .flatMap(p -> SourceRoots.read(p).lines().map(line -> p + ": " + line))
              .map(line -> Pattern.compile(": ([^#:=\\s]+):([^:=]+):([^=]+)=").matcher(line))
              .filter(java.util.regex.Matcher::find)
              .map(m ->
                  "<component group=\""
                      + m.group(1)
                      + "\" name=\""
                      + m.group(2)
                      + "\" version=\""
                      + m.group(3)
                      + "\">")
              .filter(component -> !metadata.contains(component))
              .distinct()
              .toList();
    }

    assertThat(metadata)
        .contains("<verify-metadata>true</verify-metadata>")
        .doesNotContain("<trusted-artifacts>");
    assertThat(unverified).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"core", "engine-archunit", "engine-scan", "gradle-plugin", "junit"})
  void lockRewriteFromTheRootLeavesIncludedBuildLockfileUnchanged(String module) {
    assertThat(SourceRoots.read(Path.of("fitness", module, "gradle.lockfile")))
        .contains("run: ./gradlew :fitness:" + module + ":dependencies --write-locks");
  }

  @Test
  void lockRewriteCommandAlsoWritesChecksumsBecauseTheRunFetchesLosingVersions() {
    String decision = "0189-every-dependency-action-image-and-wrapper-is-pinned.md";

    assertThat(SourceRoots.read(Path.of("../docs/decisions", decision)))
        .contains("--write-locks --write-verification-metadata sha256");
  }

  @Test
  void wrapperChangeShowsInItsChecksum() {
    assertThat(SourceRoots.read(Path.of("gradle/wrapper/gradle-wrapper.properties")))
        .containsPattern("(?m)^distributionSha256Sum=[0-9a-f]{64}$");
  }

  @Test
  void wrapperRetriesDistributionDownloadTheServerRefusedOnce() {
    assertThat(SourceRoots.read(Path.of("gradle/wrapper/gradle-wrapper.properties")))
        .containsPattern("(?m)^retries=[1-9]$");
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
  void everyDockerfileBaseAndNpmInstallIsPinnedUnlessBaseIsAnArgument() throws IOException {
    List<String> unpinned;
    try (Stream<Path> files = Files.walk(Path.of(".."), 6)) {
      unpinned =
          files
              .filter(p -> p.getFileName().toString().matches("(Dockerfile.*|.*\\.Dockerfile)"))
              .filter(p -> !p.toString().matches(".*/(\\.claude|build|node_modules|\\.git)/.*"))
              .flatMap(p -> SourceRoots.read(p).lines().map(line -> p + ": " + line))
              .filter(line -> !line.matches(".*: #.*"))
              .filter(line -> line.matches(".*: FROM .*") || line.contains("npm install"))
              .filter(line -> !line.contains("${"))
              .filter(line -> !line.matches(".*: FROM build AS \\S+$"))
              .filter(line -> !line.matches(".*: FROM \\S+:\\S+@sha256:[0-9a-f]{64}( AS \\S+)?$"))
              .filter(line -> !line.matches(".*npm install -g \\S+@\\d+\\.\\d+\\.\\d+( .*)?$"))
              .toList();
    }

    assertThat(unpinned).isEmpty();
  }

  @Test
  void appDockerfileIsFromThePublishedBaseImageByDigest() {
    String dockerfile = SourceRoots.read(Path.of("../Dockerfile"));
    String runtimeStage = dockerfile.substring(dockerfile.indexOf("FROM ${BASE}"));

    assertThat(dockerfile)
        .containsPattern("(?m)^ARG BASE=ghcr\\.io/\\S+-base:\\S+@sha256:[0-9a-f]{64}$")
        .contains("\nFROM ${BASE}\n");
    assertThat(runtimeStage).doesNotContain("apt-get");
  }

  @Test
  void standImageIsTheAppDockerfileOnThePublishedStandBaseByDigest() {
    assertThat(SourceRoots.read(Path.of("build-logic/src/main/groovy/jagt.stand.gradle")))
        .containsPattern("--build-arg BASE=ghcr\\.io/\\S+-stand-base:\\S+@sha256:[0-9a-f]{64}");
  }

  @Test
  void standBaseCopiesNodeFromDigestPinnedNodeImageAndInstallsNoNodePackage() {
    assertThat(SourceRoots.read(Path.of("../base/Dockerfile.stand")))
        .containsPattern("(?m)^FROM node:\\S+@sha256:[0-9a-f]{64} AS node$")
        .contains("COPY --from=node /usr/local/bin/node")
        .doesNotContain("nodejs");
  }

  @Test
  void pinMovesByReviewedUpdateRequest() {
    assertThat(SourceRoots.read(Path.of("../renovate.json"))).contains("config:recommended");
  }
}
