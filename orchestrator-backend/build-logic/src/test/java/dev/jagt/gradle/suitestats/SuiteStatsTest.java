package dev.jagt.gradle.suitestats;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SuiteStatsTest {

  @TempDir Path project;

  @Test
  void taskPastItsCeilingFinishesGreenWithOneWarnAndAnOverrunRecord() throws IOException {
    Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'sample'");
    Files.writeString(
        project.resolve("gradle.properties"), "suiteTrendRuns=10\nsuiteSlowdownFactor=1.5\n");
    Files.writeString(
        project.resolve("build.gradle"),
        """
        import java.util.concurrent.*
        plugins { id 'jagt.suite-stats' }
        def limits = objects.mapProperty(String, String)
        extensions.add('fitness', [limits: limits])
        tasks.register('slow') { doLast { new CountDownLatch(1).await(2, TimeUnit.SECONDS) } }
        gradle.projectsEvaluated { limits.put(':slow', 'suite-minutes|0') }
        """);

    String output =
        GradleRunner.create()
            .withProjectDir(project.toFile())
            .withPluginClasspath()
            .withArguments("slow", "--rerun-tasks")
            .build()
            .getOutput();

    assertThat(output).contains("WARN suite.overrun suite=:slow minutes=1 ceiling=0");
    assertThat(Files.readString(project.resolve("suite-stats.jsonl")))
        .contains("\"task\":\":slow\"", "\"overrun\":true", "\"fresh\":true", "\"ceiling\":0");
  }
}
