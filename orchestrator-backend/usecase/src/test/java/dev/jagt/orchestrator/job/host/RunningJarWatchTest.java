package dev.jagt.orchestrator.job.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.channel.Notification;
import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.host.Supervisor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RunningJarWatchTest {

  private final Notifications notifications = mock(Notifications.class);
  private final Host host = mock(Host.class);
  private final Supervisor supervisor = mock(Supervisor.class);

  @Test
  void saysSoOnceWhenTheJarIsRewrittenUnderTheRunningProcess(@TempDir Path dir) throws Exception {
    Path jar = Files.writeString(dir.resolve("jagt.jar"), "first build");
    when(host.jar()).thenReturn(Optional.of(jar));
    RunningJarWatch watch = new RunningJarWatch(notifications, host, supervisor);

    Files.writeString(jar, "a different build entirely");
    Files.setLastModifiedTime(jar, FileTime.fromMillis(1_700_000_000_000L));
    watch.run();
    watch.run();

    verify(notifications, times(1))
        .send(
            argThat(sent ->
                sent.topic() == Notification.Topic.INSTALL && sent.title().contains("restart")));
  }

  @Test
  void namesTheRestartVerbWhenSupervisorStands(@TempDir Path dir) throws Exception {
    Path jar = Files.writeString(dir.resolve("jagt.jar"), "first build");
    when(host.jar()).thenReturn(Optional.of(jar));
    when(supervisor.supervised()).thenReturn(true);
    RunningJarWatch watch = new RunningJarWatch(notifications, host, supervisor);

    Files.writeString(jar, "a different build entirely");
    Files.setLastModifiedTime(jar, FileTime.fromMillis(1_700_000_000_000L));
    watch.run();

    verify(notifications).send(argThat(sent -> sent.body().contains("press `restart`")));
  }

  @Test
  void staysQuietWhileTheJarIsUntouched(@TempDir Path dir) throws Exception {
    Path jar = Files.writeString(dir.resolve("jagt.jar"), "one build");
    when(host.jar()).thenReturn(Optional.of(jar));
    RunningJarWatch watch = new RunningJarWatch(notifications, host, supervisor);

    watch.run();
    watch.run();

    verifyNoInteractions(notifications);
  }

  @Test
  void treatsVanishedJarAsRewrite(@TempDir Path dir) throws Exception {
    Path jar = Files.writeString(dir.resolve("jagt.jar"), "build");
    when(host.jar()).thenReturn(Optional.of(jar));
    RunningJarWatch watch = new RunningJarWatch(notifications, host, supervisor);
    Files.delete(jar);

    watch.run();

    verify(notifications)
        .send(
            argThat(sent ->
                sent.topic() == Notification.Topic.INSTALL && sent.title().contains("restart")));
  }

  @Test
  void watchesNothingWhenTheProcessDidNotStartFromJar() {
    RunningJarWatch watch = new RunningJarWatch(notifications, host, supervisor);

    watch.run();

    verifyNoInteractions(notifications);
  }

  @ParameterizedTest
  @CsvSource({"1000,42,false", "2000,42,true", "1000,43,true"})
  void readsChangeInEitherTheStampOrTheSizeAsRewrite(
      long modifiedAt, long size, boolean rewritten) {
    var before = new RunningJarWatch.Stamp(1_000, 42);

    assertThat(RunningJarWatch.rewritten(before, new RunningJarWatch.Stamp(modifiedAt, size)))
        .isEqualTo(rewritten);
  }

  @Test
  void readsJarThatCanNoLongerBeStampedAtAllAsRewrite() {
    assertThat(RunningJarWatch.rewritten(new RunningJarWatch.Stamp(1_000, 42), null)).isTrue();
  }

  @Test
  void knowsItIsRunningTheJarTheBuildRewrites(@TempDir Path dir) throws IOException {
    Path built = Files.writeString(dir.resolve("jagt.jar"), "x");

    when(host.jar()).thenReturn(Optional.of(built));

    assertThat(new RunningJarWatch(notifications, host, supervisor).runningTheBuildsOwnJar())
        .isTrue();
  }

  @Test
  void saysNothingAboutStagedCopyTheBuildNeverTouches(@TempDir Path dir) throws IOException {
    Path staged = Files.writeString(dir.resolve("jagt-run-20260819-120000.jar"), "x");

    when(host.jar()).thenReturn(Optional.of(staged));

    assertThat(new RunningJarWatch(notifications, host, supervisor).runningTheBuildsOwnJar())
        .isFalse();
  }
}
