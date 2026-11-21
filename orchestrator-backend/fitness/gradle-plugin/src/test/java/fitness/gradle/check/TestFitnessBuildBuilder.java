package fitness.gradle.check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.gradle.testkit.runner.GradleRunner;

final class TestFitnessBuildBuilder {

  private final Path project;
  private String row =
      "scan: { engine: tool, tool: osv-scanner, measure: known-vulnerabilities, governs: security-scan, max: 0 }";
  private String sbomTask = "";
  private String inheritedPath = "";

  TestFitnessBuildBuilder(Path project) {
    this.project = project;
  }

  TestFitnessBuildBuilder row(String row) {
    this.row = row;
    return this;
  }

  TestFitnessBuildBuilder sbom() {
    this.sbomTask =
        """
        gradle.projectsEvaluated {
            tasks.named('fitnessVulnerabilities') { sbom = file('sbom.json') }
        }
        """;
    return this;
  }

  TestFitnessBuildBuilder keepingSystemPath() {
    this.inheritedPath = ":" + System.getenv("PATH");
    return this;
  }

  TestFitnessBuildBuilder scanner(String script) throws IOException {
    Path bin = Files.createDirectories(project.resolve("bin"));
    Files.writeString(bin.resolve("osv-scanner"), script).toFile().setExecutable(true);
    return this;
  }

  GradleRunner build() throws IOException {
    Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'sample'");
    Files.writeString(
        project.resolve("fitness.yml"),
        "schema: 1\nsecurity:\n  security:\n    integrity:\n      " + row + "\n");
    Files.writeString(
        project.resolve("build.gradle"),
        "plugins { id 'java'; id 'fitness' }\n"
            + "fitness { file = layout.projectDirectory.file('fitness.yml') }\n"
            + sbomTask);
    Files.writeString(project.resolve("sbom.json"), "{}");
    Map<String, String> environment = new HashMap<>(System.getenv());
    environment.put("PATH", Files.createDirectories(project.resolve("bin")) + inheritedPath);
    return GradleRunner.create()
        .withProjectDir(project.toFile())
        .withPluginClasspath()
        .withEnvironment(environment);
  }
}
