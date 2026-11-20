package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IdeSyncBuildTest {

  @Test
  void suiteStatsReadNoTaskOfAnyProjectWhenTheirParametersAreIsolated() {
    String script =
        SourceRoots.read(Path.of("build-logic/src/main/groovy/jagt.suite-stats.gradle"));

    assertThat(script).doesNotContainPattern("provider\\s*\\{\\s*allprojects");
  }

  @Test
  void spotlessPrePushHookInstallerIsDisabledInEveryProject() {
    String script =
        SourceRoots.read(Path.of("build-logic/src/main/groovy/jagt.conventions.gradle"));

    assertThat(script)
        .containsPattern(
            "tasks\\.named\\('spotlessInstallGitPrePushHook'\\)\\s*\\{\\s*enabled = false");
  }
}
