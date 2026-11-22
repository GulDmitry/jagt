package dev.jagt.gradle.pitest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DiffOwnerTest {

  @Test
  void includedBuildSourceHasNoOwnerWhenItIsInTheDiff() {
    Optional<String> owner =
        DiffOwner.projectOf(
            "orchestrator-backend/build-logic/src/main/java/dev/jagt/gradle/testsfor/Git.java");

    assertThat(owner).isEmpty();
  }

  @Test
  void rootProjectOwnsItsOwnMain() {
    Optional<String> owner =
        DiffOwner.projectOf("orchestrator-backend/src/main/java/dev/jagt/orchestrator/A.java");

    assertThat(owner).contains(":");
  }

  @Test
  void moduleOwnsItsMain() {
    Optional<String> owner =
        DiffOwner.projectOf("orchestrator-backend/adapter/src/main/java/dev/jagt/a/B.java");

    assertThat(owner).contains(":adapter");
  }
}
