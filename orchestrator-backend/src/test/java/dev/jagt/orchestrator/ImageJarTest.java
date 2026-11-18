package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImageJarTest {

  @Test
  void runtimeStageCopiesJarFromJarStageSoGradleRunsOnlyWithoutJarContext() {
    String dockerfile = SourceRoots.read(Path.of("../Dockerfile"));
    String fromJarStage = dockerfile.substring(dockerfile.indexOf("FROM build AS jar"));

    assertThat(fromJarStage)
        .contains("COPY --from=jar /jagt.jar jagt.jar")
        .doesNotContain("gradlew");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"scripts/publish-image.sh", "scripts/container-suite/compose.test.yml"})
  void imagePathPassesStagedJarAsJarContext(String file) {
    assertThat(SourceRoots.read(Path.of(file))).containsPattern("jar[=:] ?\\S*(libs|JAGT_JAR_DIR)");
  }

  @Test
  void standImageBuildsJagtImageWithJarContext() {
    assertThat(SourceRoots.read(Path.of("build-logic/src/main/groovy/jagt.stand.gradle")))
        .contains("--build-context \"jar=$2\"");
  }
}
