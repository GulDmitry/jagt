package fitness.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FitnessTaskTest {

  @TempDir Path project;

  @BeforeEach
  void aBuildWithRulesInTwoBlocks() throws IOException {
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
              gate-minutes: { engine: tool, tool: gradle, task: slow, per: gate, measure: minutes, governs: size, max: 1 }
        """);
    Files.writeString(
        project.resolve("build.gradle"),
        """
        plugins { id 'java'; id 'checkstyle'; id 'fitness' }
        fitness { file = layout.projectDirectory.file('fitness.yml') }
        tasks.register('slow') { doLast { Thread.sleep(60000) } }
        gradle.taskGraph.whenReady { tasks.slow.timeout = java.time.Duration.ofMillis(300) }
        """);
  }

  private GradleRunner gradle(String... arguments) {
    return GradleRunner.create()
        .withProjectDir(project.toFile())
        .withPluginClasspath()
        .withArguments(arguments);
  }

  @Test
  void theFitnessTaskDependsOnTheRulesAndEveryToolRowOfTheTree() {
    String output = gradle("fitness", "--dry-run").build().getOutput();

    assertThat(output)
        .contains(":fitnessFitnessFunctions", ":fitnessCheckstyle", ":fitness-suite-minutes", ":fitness-gate-minutes");
  }

  @Test
  void aBlockLeavesOutTheToolRowsOfTheOthers() {
    String output =
        gradle("fitness", "--dry-run", "-Pfitness.block=technical.maintainability").build().getOutput();

    assertThat(output).contains(":fitnessFitnessFunctions", ":fitnessCheckstyle").doesNotContain(":fitness-suite-minutes");
  }

  @Test
  void aRowMeasuredByASuiteRunNamesTheSuiteAndTheNumber() {
    String output = gradle("fitness-suite-minutes").build().getOutput();

    assertThat(output).contains("fitness: suite-minutes: measured by test, max 7");
  }

  @Test
  void aRowMeasuredByASuiteRunSaysNoValueYetBeforeAnyMeasure() {
    String output = gradle("fitness-suite-minutes").build().getOutput();

    assertThat(output).contains("fitness: suite-minutes: measured by test, max 7, no value yet");
  }

  @Test
  void aRowMeasuredByASuiteRunPrintsItsLastMeasuredValue() throws IOException {
    Files.createDirectories(project.resolve("build/fitness/last-values"));
    Files.writeString(project.resolve("build/fitness/last-values/suite-minutes"), "5");

    String output = gradle("fitness-suite-minutes").build().getOutput();

    assertThat(output).contains("fitness: suite-minutes: measured by test, max 7, last value 5");
  }

  @Test
  void aBlockOutsideTheReservedTreeFailsNamingIt() {
    String output = gradle("fitness", "--dry-run", "-Pfitness.block=technical.speed").buildAndFail().getOutput();

    assertThat(output).contains("technical.speed");
  }

  @Test
  void aGateStoppedForItsLimitNamesTheFitnessFunctionTheRunItsMinutesAndTheLimit() {
    String output = gradle("slow", "-Pfitness.block=operational").buildAndFail().getOutput();

    assertThat(output).contains("fitness: gate-minutes: :slow ran past its limit of 1 minutes");
  }
}
