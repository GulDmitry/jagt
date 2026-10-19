package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RunningJarWatchTest {

    private final Notifications notifications = mock(Notifications.class);
    private final Host host = mock(Host.class);

    @Test
    void saysSoOnceWhenTheJarIsRewrittenUnderTheRunningProcess(@TempDir Path dir) throws Exception {
        Path jar = Files.writeString(dir.resolve("jagt.jar"), "first build");
        when(host.jar()).thenReturn(Optional.of(jar));
        RunningJarWatch watch = new RunningJarWatch(notifications, host);

        Files.writeString(jar, "a different build entirely");
        Files.setLastModifiedTime(jar, FileTime.fromMillis(1_700_000_000_000L));
        watch.run();
        watch.run();

        verify(notifications, times(1)).send(argThat(sent -> sent.topic() == Notification.Topic.INSTALL
                && sent.title().contains("restart")));
    }

    @Test
    void staysQuietWhileTheJarIsUntouched(@TempDir Path dir) throws Exception {
        Path jar = Files.writeString(dir.resolve("jagt.jar"), "one build");
        when(host.jar()).thenReturn(Optional.of(jar));
        RunningJarWatch watch = new RunningJarWatch(notifications, host);

        watch.run();
        watch.run();

        verifyNoInteractions(notifications);
    }

    @Test
    void treatsAVanishedJarAsARewrite(@TempDir Path dir) throws Exception {
        Path jar = Files.writeString(dir.resolve("jagt.jar"), "build");
        when(host.jar()).thenReturn(Optional.of(jar));
        RunningJarWatch watch = new RunningJarWatch(notifications, host);
        Files.delete(jar);

        watch.run();

        verify(notifications).send(argThat(sent -> sent.topic() == Notification.Topic.INSTALL
                && sent.title().contains("restart")));
    }

    @Test
    void watchesNothingWhenTheProcessDidNotStartFromAJar() {
        RunningJarWatch watch = new RunningJarWatch(notifications, host);

        watch.run();

        verifyNoInteractions(notifications);
    }

    @ParameterizedTest
    @CsvSource({"1000,42,false", "2000,42,true", "1000,43,true"})
    void readsAChangeInEitherTheStampOrTheSizeAsARewrite(long modifiedAt, long size, boolean rewritten) {
        var before = new RunningJarWatch.Stamp(1_000, 42);

        assertThat(RunningJarWatch.rewritten(before, new RunningJarWatch.Stamp(modifiedAt, size)))
                .isEqualTo(rewritten);
    }

    @Test
    void readsAJarThatCanNoLongerBeStampedAtAllAsARewrite() {
        assertThat(RunningJarWatch.rewritten(new RunningJarWatch.Stamp(1_000, 42), null)).isTrue();
    }

    @Test
    void knowsItIsRunningTheJarTheBuildRewrites(@TempDir Path dir) throws IOException {
        Path built = Files.writeString(dir.resolve("jagt.jar"), "x");

        when(host.jar()).thenReturn(Optional.of(built));

        assertThat(new RunningJarWatch(notifications, host).runningTheBuildsOwnJar()).isTrue();
    }

    @Test
    void saysNothingAboutAStagedCopyTheBuildNeverTouches(@TempDir Path dir) throws IOException {
        Path staged = Files.writeString(dir.resolve("jagt-run-20260819-120000.jar"), "x");

        when(host.jar()).thenReturn(Optional.of(staged));

        assertThat(new RunningJarWatch(notifications, host).runningTheBuildsOwnJar()).isFalse();
    }
}
