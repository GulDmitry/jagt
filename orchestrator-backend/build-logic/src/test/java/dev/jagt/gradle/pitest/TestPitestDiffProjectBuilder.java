package dev.jagt.gradle.pitest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;

final class TestPitestDiffProjectBuilder {

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

  private final Path project;

  TestPitestDiffProjectBuilder(Path project) {
    this.project = project;
  }

  GradleRunner laidOut(String changedLines) throws IOException {
    Files.writeString(project.resolve("settings.gradle"), "");
    Files.writeString(project.resolve("build.gradle"), BUILD.formatted(TASK_CLASSES));
    Files.writeString(project.resolve("lines.txt"), changedLines);
    Files.writeString(project.resolve("mutations.xml"), REPORT);
    return GradleRunner.create().withProjectDir(project.toFile()).withArguments("survivors");
  }
}
