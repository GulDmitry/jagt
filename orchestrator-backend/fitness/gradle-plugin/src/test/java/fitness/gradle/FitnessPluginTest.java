package fitness.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FitnessPluginTest {

  @TempDir Path project;

  @Test
  void handsTheSuiteAndCheckstyleTheNumbersOfTheirRows() throws IOException {
    Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'sample'");
    Files.writeString(
        project.resolve("fitness.yml"),
        """
        schema: 1
        technical:
          maintainability:
            analysability:
              checkstyle: { engine: tool, tool: checkstyle, measure: warnings, governs: linter, max: 3 }
        operational:
          performance-efficiency:
            time-behaviour:
              suite-minutes: { engine: tool, tool: gradle, per: suite, measure: minutes, governs: size, max: 7 }
        """);
    Files.writeString(
        project.resolve("build.gradle"),
        """
        plugins { id 'java'; id 'checkstyle'; id 'fitness' }
        fitness { file = layout.projectDirectory.file('fitness.yml') }
        gradle.projectsEvaluated {
            tasks.register('show') { doLast { println "limits=${tasks.test.timeout.get()} ${checkstyle.maxWarnings}" } }
        }
        """);

    String output =
        GradleRunner.create()
            .withProjectDir(project.toFile())
            .withPluginClasspath()
            .withArguments("show", "-q")
            .build()
            .getOutput();

    assertThat(output).contains("limits=PT7M 3");
  }

  @Test
  void theTestTaskReadsEveryTrackedFileOfTheRepository() throws Exception {
    Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'sample'");
    Files.writeString(
        project.resolve("fitness.yml"),
        """
        schema: 1
        technical:
          maintainability:
            analysability:
              checkstyle: { engine: tool, tool: checkstyle, measure: warnings, governs: linter, max: 3 }
        """);
    Files.writeString(
        project.resolve("build.gradle"),
        """
        plugins { id 'java'; id 'checkstyle'; id 'fitness' }
        fitness { file = layout.projectDirectory.file('fitness.yml') }
        gradle.projectsEvaluated {
            tasks.register('show') { doLast { println "inputs=${tasks.test.inputs.files.files*.name.sort()}" } }
        }
        """);
    Files.writeString(project.resolve("check.sh"), "echo");
    Files.writeString(project.resolve("untracked.txt"), "x");
    new ProcessBuilder("git", "init", "-q", project.toString()).inheritIO().start().waitFor();
    new ProcessBuilder("git", "-C", project.toString(), "add", "check.sh", "fitness.yml")
        .inheritIO()
        .start()
        .waitFor();

    String output =
        GradleRunner.create()
            .withProjectDir(project.toFile())
            .withPluginClasspath()
            .withArguments("show", "-q")
            .build()
            .getOutput();

    assertThat(output).contains("check.sh", "fitness.yml").doesNotContain("untracked.txt");
  }

  @Test
  void refusesAToolRowItHasNoMeasureFor() throws IOException {
    Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'sample'");
    Files.writeString(
        project.resolve("fitness.yml"),
        """
        schema: 1
        technical:
          maintainability:
            analysability:
              mystery: { engine: tool, tool: unknown, measure: anything, governs: count, max: 0 }
        """);
    Files.writeString(
        project.resolve("build.gradle"),
        """
        plugins { id 'java'; id 'fitness' }
        fitness { file = layout.projectDirectory.file('fitness.yml') }
        """);

    String output =
        GradleRunner.create()
            .withProjectDir(project.toFile())
            .withPluginClasspath()
            .withArguments("tasks")
            .buildAndFail()
            .getOutput();

    assertThat(output).contains("fitness: no measure for mystery");
  }
}
