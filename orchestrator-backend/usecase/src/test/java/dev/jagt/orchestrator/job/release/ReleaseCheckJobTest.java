package dev.jagt.orchestrator.job.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseCheckJobTest {

  private static final String SUMS =
      "fba6a4ac61e440c7309ae013e5c5c7ac0a9000c8a3bab7def760b499d587b1d0  jagt-0.25.0.jar\n";

  private final Host host = mock(Host.class);
  private final Reach reach = mock(Reach.class);

  @Test
  void keepsNewerReleaseVerifiedBesideTheRunningOne(@TempDir Path root) throws Exception {
    Path running = Files.createDirectories(root.resolve("versions").resolve("0.24.1"));
    Files.writeString(running.resolve("jagt.jar"), "running");
    when(host.jar()).thenReturn(Optional.of(running.resolve("jagt.jar")));
    FakeReleaseFeed feed = new FakeReleaseFeed(Optional.of("0.25.0"), "jar bytes", SUMS, false);

    new ReleaseCheckJob(host, feed, reach).run();

    assertThat(root.resolve("versions/0.25.0/jagt.jar")).hasContent("jar bytes");
    assertThat(running.resolve("jagt.jar")).hasContent("running");
    assertThat(root.resolve("current")).doesNotExist();
    try (Stream<Path> entries = Files.list(root)) {
      assertThat(entries.map(entry -> entry.getFileName().toString()))
          .containsExactlyInAnyOrder("versions");
    }
  }

  @Test
  void keepsTheVerifiedChecksumBesideTheKeptJar(@TempDir Path root) throws Exception {
    Path running = Files.createDirectories(root.resolve("versions").resolve("0.24.1"));
    when(host.jar()).thenReturn(Optional.of(running.resolve("jagt.jar")));
    FakeReleaseFeed feed = new FakeReleaseFeed(Optional.of("0.25.0"), "jar bytes", SUMS, false);

    new ReleaseCheckJob(host, feed, reach).run();

    assertThat(root.resolve("versions/0.25.0/jagt.jar.sha256"))
        .hasContent("fba6a4ac61e440c7309ae013e5c5c7ac0a9000c8a3bab7def760b499d587b1d0  jagt.jar\n");
  }

  @Test
  void discardsTheDownloadWhenItsChecksumDoesNotMatch(@TempDir Path root) throws Exception {
    Path running = Files.createDirectories(root.resolve("versions").resolve("0.24.1"));
    when(host.jar()).thenReturn(Optional.of(running.resolve("jagt.jar")));
    FakeReleaseFeed feed = new FakeReleaseFeed(Optional.of("0.25.0"), "tampered", SUMS, false);

    ReleaseCheckJob job = new ReleaseCheckJob(host, feed, reach);

    job.run();

    assertThat(root.resolve("versions/0.25.0")).doesNotExist();
    assertThat(root.toFile().list()).containsExactly("versions");
    assertThat(job.kept()).isEmpty();
  }

  @Test
  void doesNothingWhenNotRunningFromAnInstalledLayout(@TempDir Path folder) throws Exception {
    Path jar = Files.writeString(folder.resolve("jagt.jar"), "built here");
    when(host.jar()).thenReturn(Optional.of(jar));
    FakeReleaseFeed feed = new FakeReleaseFeed(Optional.of("0.25.0"), "jar bytes", SUMS, false);

    new ReleaseCheckJob(host, feed, reach).run();

    assertThat(feed.downloads).isZero();
    try (Stream<Path> entries = Files.list(folder)) {
      assertThat(entries.count()).isEqualTo(1);
    }
  }

  @Test
  void downloadsNothingWhenTheLatestIsAlreadyKept(@TempDir Path root) throws Exception {
    Path running = Files.createDirectories(root.resolve("versions").resolve("0.24.1"));
    Files.writeString(running.resolve("jagt.jar"), "running");
    Path kept = Files.createDirectories(root.resolve("versions").resolve("0.25.0"));
    Files.writeString(kept.resolve("jagt.jar"), "kept earlier");
    when(host.jar()).thenReturn(Optional.of(running.resolve("jagt.jar")));
    FakeReleaseFeed feed = new FakeReleaseFeed(Optional.of("0.25.0"), "jar bytes", SUMS, false);

    new ReleaseCheckJob(host, feed, reach).run();

    assertThat(feed.downloads).isZero();
    assertThat(kept.resolve("jagt.jar")).hasContent("kept earlier");
  }

  @Test
  void downloadsNothingWhenTheLatestIsNotNewer(@TempDir Path root) throws Exception {
    Path running = Files.createDirectories(root.resolve("versions").resolve("0.25.0"));
    Files.writeString(running.resolve("jagt.jar"), "running");
    when(host.jar()).thenReturn(Optional.of(running.resolve("jagt.jar")));
    FakeReleaseFeed feed = new FakeReleaseFeed(Optional.of("0.24.9"), "jar bytes", SUMS, false);

    new ReleaseCheckJob(host, feed, reach).run();

    assertThat(feed.downloads).isZero();
  }

  @Test
  void refusesVersionThatCouldLeaveTheVersionsFolder(@TempDir Path root) throws Exception {
    Path running = Files.createDirectories(root.resolve("versions").resolve("0.24.1"));
    Files.writeString(running.resolve("jagt.jar"), "running");
    when(host.jar()).thenReturn(Optional.of(running.resolve("jagt.jar")));
    FakeReleaseFeed feed =
        new FakeReleaseFeed(Optional.of("9.0.0/../../x"), "jar bytes", SUMS, false);

    new ReleaseCheckJob(host, feed, reach).run();

    assertThat(feed.downloads).isZero();
  }

  @Test
  void survivesFeedThatCannotBeReached(@TempDir Path root) throws Exception {
    Path running = Files.createDirectories(root.resolve("versions").resolve("0.24.1"));
    Files.writeString(running.resolve("jagt.jar"), "running");
    when(host.jar()).thenReturn(Optional.of(running.resolve("jagt.jar")));
    FakeReleaseFeed feed = new FakeReleaseFeed(Optional.empty(), "", "", true);

    assertThatCode(() -> new ReleaseCheckJob(host, feed, reach).run()).doesNotThrowAnyException();
    assertThat(feed.downloads).isZero();
  }

  @Test
  void marksTheBoardHeadWhenTheFeedCannotBeRead(@TempDir Path root) throws Exception {
    Path running = Files.createDirectories(root.resolve("versions").resolve("0.24.1"));
    when(host.jar()).thenReturn(Optional.of(running.resolve("jagt.jar")));
    FakeReleaseFeed feed = new FakeReleaseFeed(Optional.empty(), "", "", true);

    new ReleaseCheckJob(host, feed, reach).run();

    verify(reach)
        .marked(
            new ReachLost(
                "fake",
                "releases.example.invalid",
                List.of(),
                "fake cannot read the release feed on releases.example.invalid"));
    verify(reach, never()).reached("fake", "releases.example.invalid");
  }

  @Test
  void clearsTheMarkWhenTheFeedIsReadAgain(@TempDir Path root) throws Exception {
    Path running = Files.createDirectories(root.resolve("versions").resolve("0.24.1"));
    when(host.jar()).thenReturn(Optional.of(running.resolve("jagt.jar")));
    FakeReleaseFeed feed = new FakeReleaseFeed(Optional.of("0.24.1"), "", "", false);

    new ReleaseCheckJob(host, feed, reach).run();

    verify(reach).reached("fake", "releases.example.invalid");
    verify(reach, never()).marked(any());
  }

  @Test
  void removesStagingFoldersAnInterruptedRunLeft(@TempDir Path root) throws Exception {
    Path running = Files.createDirectories(root.resolve("versions").resolve("0.24.1"));
    when(host.jar()).thenReturn(Optional.of(running.resolve("jagt.jar")));
    Path leftover = Files.createDirectories(root.resolve(".staging.12345"));
    Files.writeString(leftover.resolve("jagt-0.25.0.jar"), "half");
    FakeReleaseFeed feed = new FakeReleaseFeed(Optional.empty(), "", "", false);

    new ReleaseCheckJob(host, feed, reach).run();

    assertThat(root.toFile().list()).containsExactly("versions");
  }

  @Test
  void namesTheNewerReleaseItKeptAsWaitingUntilItIsSwitched(@TempDir Path root) throws Exception {
    Path running = Files.createDirectories(root.resolve("versions").resolve("0.24.1"));
    when(host.jar()).thenReturn(Optional.of(running.resolve("jagt.jar")));
    FakeReleaseFeed feed = new FakeReleaseFeed(Optional.of("0.25.0"), "jar bytes", SUMS, false);
    ReleaseCheckJob job = new ReleaseCheckJob(host, feed, reach);

    job.run();
    Optional<ReleaseCheckJob.Kept> waiting = job.kept();
    job.switched("0.25.0");

    assertThat(waiting).contains(new ReleaseCheckJob.Kept(root, "0.25.0"));
    assertThat(job.kept()).isEmpty();
  }

  @Test
  void namesNothingWaitingWhenTheLatestIsNotNewer(@TempDir Path root) throws Exception {
    Path running = Files.createDirectories(root.resolve("versions").resolve("0.25.0"));
    when(host.jar()).thenReturn(Optional.of(running.resolve("jagt.jar")));
    FakeReleaseFeed feed = new FakeReleaseFeed(Optional.of("0.25.0"), "jar bytes", SUMS, false);
    ReleaseCheckJob job = new ReleaseCheckJob(host, feed, reach);

    job.run();

    assertThat(job.kept()).isEmpty();
  }
}
