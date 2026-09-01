package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RunningJarWatchTest {

    private final Notifications notifications = mock(Notifications.class);

    @Test
    void saysSoOnceWhenTheJarIsRewrittenUnderTheRunningProcess(@TempDir Path dir) throws Exception {
        Path jar = Files.writeString(dir.resolve("jagt.jar"), "first build");
        RunningJarWatch watch = new RunningJarWatch(notifications, jar);

        Files.writeString(jar, "a different build entirely");
        Files.setLastModifiedTime(jar, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        watch.run();
        watch.run();

        verify(notifications, times(1)).send(argThat(sent -> sent.topic() == Notification.Topic.INSTALL
                && sent.title().contains("restart")));
    }

    @Test
    void staysQuietWhileTheJarIsUntouched(@TempDir Path dir) throws Exception {
        Path jar = Files.writeString(dir.resolve("jagt.jar"), "one build");
        RunningJarWatch watch = new RunningJarWatch(notifications, jar);

        watch.run();
        watch.run();

        verifyNoInteractions(notifications);
    }

    @Test
    void treatsAVanishedJarAsARewrite(@TempDir Path dir) throws Exception {
        Path jar = Files.writeString(dir.resolve("jagt.jar"), "build");
        RunningJarWatch watch = new RunningJarWatch(notifications, jar);
        Files.delete(jar);

        watch.run();

        verify(notifications).send(argThat(sent -> sent.topic() == Notification.Topic.INSTALL
                && sent.title().contains("restart")));
    }

    @Test
    void watchesNothingWhenTheProcessDidNotStartFromAJar() {
        RunningJarWatch watch = new RunningJarWatch(notifications, null);

        watch.run();

        verifyNoInteractions(notifications);
    }

    @Test
    void findsTheJarTheProcessWasStartedFrom(@TempDir Path dir) throws Exception {
        Path jar = Files.writeString(dir.resolve("jagt.jar"), "build");

        assertThat(RunningJarWatch.jarFromClassPath(jar.toString(), Files::isRegularFile)).isEqualTo(jar);
    }

    @Test
    void findsNoJarWhenSomethingElseIsOnTheClassPathToo(@TempDir Path dir) throws Exception {
        Path jar = Files.writeString(dir.resolve("jagt.jar"), "build");

        assertThat(RunningJarWatch.jarFromClassPath(jar + File.pathSeparator + "other.jar",
                Files::isRegularFile)).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"does-not-exist.jar", "classes"})
    void findsNoJarWhenTheClassPathNamesNoFile(String classPath) {
        assertThat(RunningJarWatch.jarFromClassPath(classPath, Files::isRegularFile)).isNull();
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

        assertThat(new RunningJarWatch(mock(Notifications.class), built).runningTheBuildsOwnJar()).isTrue();
    }

    @Test
    void saysNothingAboutAStagedCopyTheBuildNeverTouches(@TempDir Path dir) throws IOException {
        Path staged = Files.writeString(dir.resolve("jagt-run-20260819-120000.jar"), "x");

        assertThat(new RunningJarWatch(mock(Notifications.class), staged).runningTheBuildsOwnJar()).isFalse();
    }
}
