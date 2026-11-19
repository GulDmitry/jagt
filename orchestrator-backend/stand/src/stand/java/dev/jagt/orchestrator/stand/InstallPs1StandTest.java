package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstallPs1StandTest {

  private static final String JAR_SHA256 =
      "fe9b0239e80bc2d809c9792f8b6c24882125e5b52b65627bbf6be97877f80632";
  private static final String WRONG_SHA256 =
      "1d4d0c9bbf8c4a4c0f4f1c5c9d1b1a64b1d8b1c1c8f0d7c1d0f1c6d8c9c6a1f2";

  @Test
  void installsLayoutAndLauncherWhenChecksumsMatch(@TempDir Path dir) throws Exception {
    assumeTrue(new ProcessBuilder("docker", "info").start().waitFor() == 0);
    Path work = dir.toRealPath();
    try (FakeInstallFeed feed = new FakeInstallFeed(work, JAR_SHA256)) {
      Process installed = feed.runInstaller();
      String log = new String(installed.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertThat(installed.waitFor(300, TimeUnit.SECONDS)).as(log).isTrue();

      assertThat(installed.exitValue()).as(log).isZero();
      assertThat(work.resolve("home/versions/1.2.3/jagt.jar")).hasContent("fake jar");
      assertThat(work.resolve("home/runtime/bin/java")).exists();
      assertThat(work.resolve("home/current")).hasContent("1.2.3");
      assertThat(work.resolve("bin/jagt.cmd")).content().contains("jagt.jar").contains("java");
    }
  }

  @Test
  void installsNothingWhenJarChecksumMismatches(@TempDir Path dir) throws Exception {
    assumeTrue(new ProcessBuilder("docker", "info").start().waitFor() == 0);
    Path work = dir.toRealPath();
    try (FakeInstallFeed feed = new FakeInstallFeed(work, WRONG_SHA256)) {
      Process installed = feed.runInstaller();
      String log = new String(installed.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertThat(installed.waitFor(300, TimeUnit.SECONDS)).as(log).isTrue();

      assertThat(installed.exitValue()).isNotZero();
      assertThat(log).contains("checksum mismatch");
      assertThat(work.resolve("home")).doesNotExist();
      assertThat(work.resolve("bin")).doesNotExist();
    }
  }
}
