package fitness.gradle.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RequirementsCheckTest {

  @TempDir Path project;

  @Test
  void failsAsMissingToolWhenARequiredRowNamesAToolTheMachineLacks() throws IOException {
    String output =
        new TestFitnessBuildBuilder(project)
            .build()
            .withArguments("fitnessRequirements", "--stacktrace")
            .buildAndFail()
            .getOutput();

    assertThat(output).contains("fitness.core.FitnessConfigException: MISSING_TOOL: security.security.integrity.scan needs osv-scanner");
  }

  @Test
  void passesWhenTheToolOfAnOptionalRowIsMissing() throws IOException {
    String output =
        new TestFitnessBuildBuilder(project)
            .row(
                "scan: { engine: tool, tool: osv-scanner, measure: known-vulnerabilities, governs: security-scan, max: 0, optional: true }")
            .build()
            .withArguments("fitnessRequirements")
            .build()
            .getOutput();

    assertThat(output).contains("BUILD SUCCESSFUL");
  }
}
