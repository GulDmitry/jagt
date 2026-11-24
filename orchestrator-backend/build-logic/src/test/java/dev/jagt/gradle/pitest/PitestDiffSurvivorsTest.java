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

  private static final String CHANGED = "a/A.java:%d\n";

  @Test
  void secondRunIsUpToDateAndKeepsTheFirstSurvivorsWhenTheDiffIsTheSame(@TempDir Path project)
      throws Exception {
    GradleRunner runner = new TestPitestDiffProjectBuilder(project).laidOut(CHANGED.formatted(7));
    runner.build();
    String first = Files.readString(project.resolve("survivors.txt"));

    BuildResult second = runner.build();

    assertThat(second.task(":survivors").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
    assertThat(Files.readString(project.resolve("survivors.txt"))).isEqualTo(first);
    assertThat(first).contains(CHANGED.formatted(7).strip() + " run removed call");
  }

  @Test
  void runsAgainWhenTheChangedLinesDiffer(@TempDir Path project) throws Exception {
    GradleRunner runner = new TestPitestDiffProjectBuilder(project).laidOut(CHANGED.formatted(7));
    runner.build();
    Files.writeString(project.resolve("lines.txt"), CHANGED.formatted(8));

    BuildResult second = runner.build();

    assertThat(second.task(":survivors").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(Files.readString(project.resolve("survivors.txt")))
        .contains("no mutant survives on a changed line");
  }
}
