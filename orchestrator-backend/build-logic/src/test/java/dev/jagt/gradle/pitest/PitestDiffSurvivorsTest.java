package dev.jagt.gradle.pitest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PitestDiffSurvivorsTest {

  private static final String BUILD =
      """
      buildscript { dependencies { classpath files('%s') } }
      tasks.register('survivors', dev.jagt.gradle.pitest.PitestDiffSurvivors) {
        changedLines.set(file('lines.txt'))
        reports.from('mutations.xml')
        survivors.set(file('survivors.txt'))
      }
      """;

  private static final String TASK_CLASSES =
      PitestDiffSurvivors.class.getProtectionDomain().getCodeSource().getLocation().getPath();

  private static final String REPORT =
      """
      <mutations>
        <mutation status='SURVIVED'><sourceFile>A.java</sourceFile>\
      <mutatedClass>a.A</mutatedClass><mutatedMethod>run</mutatedMethod>\
      <lineNumber>7</lineNumber><description>removed call</description><indexes><index>1</index></indexes></mutation>
      </mutations>
      """;

  @Test
  void secondRunIsUpToDateAndKeepsTheFirstSurvivorsWhenTheDiffIsTheSame(@TempDir Path project)
      throws Exception {
    Files.writeString(project.resolve("settings.gradle"), "");
    Files.writeString(project.resolve("build.gradle"), BUILD.formatted(TASK_CLASSES));
    Files.writeString(project.resolve("lines.txt"), "a/A.java:7\n");
    Files.writeString(project.resolve("mutations.xml"), REPORT);
    GradleRunner runner =
        GradleRunner.create()
            .withProjectDir(project.toFile())
            .withArguments("survivors");
    runner.build();
    String first = Files.readString(project.resolve("survivors.txt"));

    BuildResult second = runner.build();

    assertThat(second.task(":survivors").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
    assertThat(Files.readString(project.resolve("survivors.txt"))).isEqualTo(first);
    assertThat(first).contains("a/A.java:7 run removed call");
  }

  @Test
  void runsAgainWhenTheChangedLinesDiffer(@TempDir Path project) throws Exception {
    Files.writeString(project.resolve("settings.gradle"), "");
    Files.writeString(project.resolve("build.gradle"), BUILD.formatted(TASK_CLASSES));
    Files.writeString(project.resolve("lines.txt"), "a/A.java:7\n");
    Files.writeString(project.resolve("mutations.xml"), REPORT);
    GradleRunner runner =
        GradleRunner.create()
            .withProjectDir(project.toFile())
            .withArguments("survivors");
    runner.build();
    Files.writeString(project.resolve("lines.txt"), "a/A.java:8\n");

    BuildResult second = runner.build();

    assertThat(second.task(":survivors").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(Files.readString(project.resolve("survivors.txt")))
        .contains("no mutant survives on a changed line");
  }
}
