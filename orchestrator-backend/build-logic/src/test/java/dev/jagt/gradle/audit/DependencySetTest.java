package dev.jagt.gradle.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DependencySetTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "orchestrator-backend/adapter/gradle.lockfile",
        "orchestrator-backend/build-logic/buildscript-gradle.lockfile",
        "orchestrator-backend/gradle/libs.versions.toml",
        "orchestrator-backend/gradle/verification-metadata.xml",
        "board/package-lock.json"
      })
  void scansWhenTheDependencySetChanged(String path) {
    assertThat(DependencySet.scanNeeded("origin/main", base -> List.of("README.md", path)))
        .isTrue();
  }

  @Test
  void scansNothingWhenOnlyCodeAndDocsChanged() {
    assertThat(
            DependencySet.scanNeeded(
                "origin/main",
                base -> List.of("orchestrator-backend/core/src/main/java/A.java", "docs/a.md")))
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"all", " "})
  void scansAlwaysWhenTheBaseIsAllOrBlank(String base) {
    assertThat(DependencySet.scanNeeded(base, ignored -> List.of())).isTrue();
  }

  @Test
  void scansAlwaysWhenThereIsNoBase() {
    assertThat(DependencySet.scanNeeded(null, ignored -> List.of())).isTrue();
  }

  @Test
  void scansWhenTheBaseCannotBeDiffed() {
    assertThat(
            DependencySet.scanNeeded(
                "origin/main",
                base -> {
                  throw new IllegalStateException();
                }))
        .isTrue();
  }
}
