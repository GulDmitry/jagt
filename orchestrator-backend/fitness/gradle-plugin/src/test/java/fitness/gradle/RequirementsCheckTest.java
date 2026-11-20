package fitness.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RequirementsCheckTest {

  @TempDir Path project;

  @Test
  void failsAsMissingToolWhenARequiredRowNamesAToolTheMachineLacks() throws IOException {
    Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'sample'");
    Files.writeString(
        project.resolve("fitness.yml"),
        """
        schema: 1
        security:
          security:
            integrity:
              scan: { engine: tool, tool: osv-scanner, measure: known-vulnerabilities, governs: security-scan, max: 0 }
        """);
    Files.writeString(
        project.resolve("build.gradle"),
        """
        plugins { id 'java'; id 'fitness' }
        fitness { file = layout.projectDirectory.file('fitness.yml') }
        """);
    Map<String, String> environment = new HashMap<>(System.getenv());
    environment.put("PATH", Files.createDirectories(project.resolve("bin")).toString());

    String output =
        GradleRunner.create()
            .withProjectDir(project.toFile())
            .withPluginClasspath()
            .withEnvironment(environment)
            .withArguments("fitnessRequirements", "--stacktrace")
            .buildAndFail()
            .getOutput();

    assertThat(output).contains("fitness.core.FitnessConfigException: MISSING_TOOL: security.security.integrity.scan needs osv-scanner");
  }

  @Test
  void passesWhenTheToolOfAnOptionalRowIsMissing() throws IOException {
    Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'sample'");
    Files.writeString(
        project.resolve("fitness.yml"),
        """
        schema: 1
        security:
          security:
            integrity:
              scan: { engine: tool, tool: osv-scanner, measure: known-vulnerabilities, governs: security-scan, max: 0, optional: true }
        """);
    Files.writeString(
        project.resolve("build.gradle"),
        """
        plugins { id 'java'; id 'fitness' }
        fitness { file = layout.projectDirectory.file('fitness.yml') }
        """);
    Map<String, String> environment = new HashMap<>(System.getenv());
    environment.put("PATH", Files.createDirectories(project.resolve("bin")).toString());

    String output =
        GradleRunner.create()
            .withProjectDir(project.toFile())
            .withPluginClasspath()
            .withEnvironment(environment)
            .withArguments("fitnessRequirements")
            .build()
            .getOutput();

    assertThat(output).contains("BUILD SUCCESSFUL");
  }
}
