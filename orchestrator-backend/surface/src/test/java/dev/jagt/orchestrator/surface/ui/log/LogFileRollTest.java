package dev.jagt.orchestrator.surface.ui.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.support.EnvironmentPostProcessorApplicationListener;
import org.springframework.mock.env.MockEnvironment;

class LogFileRollTest {

  private final LogFileRoll roll =
      new LogFileRoll(
          Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC), System.err);
  private int freePort;

  @BeforeEach
  void pickFreePort() throws IOException {
    try (ServerSocket probe = new ServerSocket()) {
      probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
      freePort = probe.getLocalPort();
    }
  }

  @Test
  void rollsThePreviousRunsLogIntoDotOneBeforeTheAppenderOpensIt(@TempDir Path root)
      throws IOException {
    Path log = root.resolve("jagt.log");
    Files.writeString(log, "yesterday\n");
    Files.writeString(root.resolve("jagt.log.1"), "the day before");
    Files.writeString(root.resolve("jagt.log.2026-08-17.0.gz"), "older still");
    Files.writeString(root.resolve("keep-me.log"), "another program's");
    roll.apply(
        new MockEnvironment()
            .withProperty("logging.file.name", log.toString())
            .withProperty("server.port", String.valueOf(freePort)));

    assertThat(log).doesNotExist();
    assertThat(root.resolve("jagt.log.1")).content().contains("yesterday");
    assertThat(root.resolve("jagt.log.2026-08-17.0.gz")).doesNotExist();
    assertThat(root.resolve("keep-me.log")).exists();
  }

  @Test
  void keepsTheOlderRunAsDatedArchiveWhenAnotherRunStarts(@TempDir Path root) throws IOException {
    Path log = root.resolve("jagt.log");
    Files.writeString(log, "yesterday\n");
    Path previous = root.resolve("jagt.log.1");
    Files.writeString(previous, "the day before");
    Files.setLastModifiedTime(previous, FileTime.from(Instant.parse("2100-03-04T05:06:07Z")));

    roll.start(log);

    assertThat(root.resolve("jagt.log.2100-03-04-050607")).content().isEqualTo("the day before");
    assertThat(previous).content().contains("yesterday");
  }

  @Test
  void deletesAnArchiveOlderThanFourteenDaysAndKeepsOneYoungerThanThat(@TempDir Path root)
      throws IOException {
    Path log = root.resolve("jagt.log");
    Files.writeString(log, "yesterday\n");
    Path old = root.resolve("jagt.log.2026-01-01-000000");
    Files.writeString(old, "stale");
    Files.setLastModifiedTime(old, FileTime.from(Instant.parse("2026-10-26T12:00:00Z")));
    Path recent = root.resolve("jagt.log.2026-01-02-000000");
    Files.writeString(recent, "fresh");
    Files.setLastModifiedTime(recent, FileTime.from(Instant.parse("2026-10-28T12:00:00Z")));

    roll.start(log);

    assertThat(old).doesNotExist();
    assertThat(recent).exists();
  }

  @Nested
  class WhereTwoArchivesWeighOneHundredMegabytesEach {

    @TempDir Path root;
    private Path log;
    private Path older;
    private Path newer;

    @BeforeEach
    void writeTwoSparseArchives() throws IOException {
      log = root.resolve("jagt.log");
      Files.writeString(log, "yesterday\n");
      older = root.resolve("jagt.log.2026-01-01-000000");
      try (RandomAccessFile sparse = new RandomAccessFile(older.toFile(), "rw")) {
        sparse.setLength(100L * 1024L * 1024L);
      }
      Files.setLastModifiedTime(older, FileTime.from(Instant.parse("2026-11-08T12:00:00Z")));
      newer = root.resolve("jagt.log.2026-01-02-000000");
      try (RandomAccessFile sparse = new RandomAccessFile(newer.toFile(), "rw")) {
        sparse.setLength(100L * 1024L * 1024L);
      }
      Files.setLastModifiedTime(newer, FileTime.from(Instant.parse("2026-11-09T12:00:00Z")));
    }

    @Test
    void deletesTheOldestArchivesOncePastTwoHundredMegabytesInTotal() throws IOException {
      Path oldest = root.resolve("jagt.log.2025-12-31-000000");
      try (RandomAccessFile sparse = new RandomAccessFile(oldest.toFile(), "rw")) {
        sparse.setLength(100L * 1024L * 1024L);
      }
      Files.setLastModifiedTime(oldest, FileTime.from(Instant.parse("2026-11-07T12:00:00Z")));

      roll.start(log);

      assertThat(oldest).doesNotExist();
      assertThat(older).exists();
      assertThat(newer).exists();
    }

    @Test
    void keepsArchivesTotallingExactlyTwoHundredMegabytes() {
      roll.start(log);

      assertThat(older).exists();
      assertThat(newer).exists();
    }
  }

  @Test
  void leavesDirectoryNamedAsTheLogAndItsDottedSiblingsInPlace(@TempDir Path root)
      throws IOException {
    Path folder = Files.createDirectories(root.resolve("surface"));
    Files.writeString(folder.resolve("Kept.java"), "class Kept {}");
    final Path sibling = Files.createDirectories(root.resolve("surface.gradle"));
    roll.apply(
        new MockEnvironment()
            .withProperty("logging.file.name", folder.toString())
            .withProperty("server.port", String.valueOf(freePort)));

    assertThat(folder.resolve("Kept.java")).exists();
    assertThat(sibling).exists();
    assertThat(root.resolve("surface.1")).doesNotExist();
  }

  @Test
  void rollsTheMasterFeedAsWellAsTheMainLog(@TempDir Path root) throws IOException {
    Path feed = root.resolve("master.log");
    Files.writeString(feed, "yesterday's steps\n");
    roll.apply(
        new MockEnvironment()
            .withProperty("logging.file.name", root.resolve("jagt.log").toString())
            .withProperty("logging.feed.name", feed.toString())
            .withProperty("server.port", String.valueOf(freePort)));

    assertThat(root.resolve("master.log.1")).content().contains("yesterday's steps");
  }

  @Test
  void leavesTheLogOfJagtThatIsStillListeningAlone(@TempDir Path root) throws Exception {
    Path log = root.resolve("jagt.log");
    Files.writeString(log, "the running instance's own record\n");

    try (ServerSocket occupied = new ServerSocket()) {
      occupied.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
      boolean rolled =
          roll.apply(
              new MockEnvironment()
                  .withProperty("logging.file.name", log.toString())
                  .withProperty("server.port", String.valueOf(occupied.getLocalPort())));

      assertThat(rolled).isFalse();
    }

    assertThat(log).content().contains("the running instance's own record");
  }

  @Test
  void rollsTheLogWhenTheConfiguredPortIsNotNumber(@TempDir Path root) throws IOException {
    Path log = root.resolve("jagt.log");
    Files.writeString(log, "yesterday\n");

    boolean rolled =
        roll.apply(
            new MockEnvironment()
                .withProperty("logging.file.name", log.toString())
                .withProperty("server.port", "eighty"));

    assertThat(rolled).isTrue();
    assertThat(log).doesNotExist();
  }

  @Test
  void tellsTheHumanWhichLogItCouldNotRollAndStillStarts(@TempDir Path root) throws IOException {
    Path stuck = root.resolve("jagt.log");
    Files.writeString(stuck, "yesterday\n");
    Path archive = Files.createDirectories(root.resolve("jagt.log.1"));
    Files.writeString(archive.resolve("inside"), "keeps the archive from being replaced");
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    boolean rolled =
        new LogFileRoll(
                Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC),
                new PrintStream(err, true, StandardCharsets.UTF_8))
            .apply(
                new MockEnvironment()
                    .withProperty("logging.file.name", stuck.toString())
                    .withProperty("server.port", String.valueOf(freePort)));

    assertThat(rolled).isTrue();
    assertThat(err.toString(StandardCharsets.UTF_8)).contains("Could not roll " + stuck);
  }

  @Test
  void rollsNothingWhenNoLogFileIsConfigured() {
    assertThat(roll.apply(new MockEnvironment())).isFalse();
  }

  @Test
  void runsAfterTheConfigFilesAreReadAndBeforeLoggingIsInitialised() {
    assertThat(roll.getOrder())
        .isGreaterThan(new EnvironmentPostProcessorApplicationListener().getOrder())
        .isLessThan(new LoggingApplicationListener().getOrder());
  }
}
