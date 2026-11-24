package dev.jagt.gradle.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BaseImagesTest {

  private static final Path REPOSITORY = Path.of("/r");

  @Test
  void coversEveryImageTheRegistryWorkflowPublishes() {
    assertThat(BaseImages.all())
        .extracting(BaseImages.Image::dockerfile)
        .containsExactly("Dockerfile", "Dockerfile.stand", "Dockerfile.ci");
  }

  @ParameterizedTest
  @ValueSource(strings = {"base/Dockerfile.ci", "orchestrator-backend/scripts/linux-test-deps.sh"})
  void scansWhenAnImageInputChanged(String path) {
    assertThat(BaseImages.scanNeeded("origin/main", base -> List.of("docs/a.md", path))).isTrue();
  }

  @Test
  void skipsWhenNoImageInputChanged() {
    assertThat(
            BaseImages.scanNeeded(
                "origin/main", base -> List.of("orchestrator-backend/scripts/ci-local.sh")))
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"all", " "})
  void scansAlwaysWhenTheBaseIsAllOrBlank(String base) {
    assertThat(BaseImages.scanNeeded(base, ignored -> List.of())).isTrue();
  }

  @Test
  void scansWhenTheDiffCannotBeRead() {
    assertThat(
            BaseImages.scanNeeded(
                "origin/main",
                base -> {
                  throw new IllegalStateException("no such ref");
                }))
        .isTrue();
  }

  @Test
  void buildsForAmd64WithTheScriptsContextAndNoBaseArgumentForAnIndependentImage() {
    assertThat(BaseImages.build(REPOSITORY, BaseImages.all().get(2)))
        .containsSequence("--platform", "linux/amd64")
        .containsSequence("--build-context", "scripts=/r/orchestrator-backend/scripts")
        .containsSequence("--file", "/r/base/Dockerfile.ci")
        .doesNotContain("--build-arg")
        .endsWith("/r/base");
  }

  @Test
  void buildsTheStandImageOnTheLocallyBuiltBase() {
    assertThat(BaseImages.build(REPOSITORY, BaseImages.all().get(1)))
        .containsSequence("--build-arg", "BASE=jagt-scan-base:local");
  }

  @Test
  void scansTheSavedArchiveThroughTheSharedScriptInAnAmd64Container() {
    assertThat(BaseImages.scan(REPOSITORY, Path.of("/t/ci.tar")))
        .containsSequence("--platform", "linux/amd64")
        .contains("/t/ci.tar:/image.tar:ro")
        .containsSequence(
            "/scripts/vulnerability-scan.sh", "scan", "image", "--archive", "/image.tar");
  }

  @Test
  void savesTheImageByItsTag() {
    assertThat(BaseImages.save(BaseImages.all().get(0), Path.of("/t/base.tar")))
        .containsExactly("docker", "save", "--output", "/t/base.tar", "jagt-scan-base:local");
  }
}
