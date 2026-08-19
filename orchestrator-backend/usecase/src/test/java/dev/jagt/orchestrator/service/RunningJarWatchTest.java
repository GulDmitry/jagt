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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The symptom this exists for: `./gradlew build` rewrites the jar in place, so a running jagt keeps reading a
 * file that changed, and every class loaded afterwards fails — /status and /stats answer 500 while
 * the board still renders. Twice that looked like a bug in those endpoints.
 */
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

    /** A `clean` removes the file: that is a rewrite as far as class loading is concerned. */
    @Test
    void treatsAVanishedJarAsARewrite(@TempDir Path dir) throws Exception {
        Path jar = Files.writeString(dir.resolve("jagt.jar"), "build");
        RunningJarWatch watch = new RunningJarWatch(notifications, jar);
        Files.delete(jar);

        watch.run();

        verify(notifications).send(argThat(sent -> sent.topic() == Notification.Topic.INSTALL
                && sent.title().contains("restart")));
    }

    /** Run from an IDE, a test or `bootRun` there is no jar — the watch must be inert, not noisy. */
    @Test
    void watchesNothingWhenTheProcessDidNotStartFromAJar() {
        RunningJarWatch watch = new RunningJarWatch(notifications, null);

        watch.run();

        verifyNoInteractions(notifications);
    }

    /**
     * The first implementation asked the protection domain and got a {@code jar:nested:} URL it could not turn
     * into a path, so the watch shipped inert. `java -jar x.jar` puts exactly that jar on the classpath.
     */
    @Test
    void findsTheJarTheProcessWasStartedFrom(@TempDir Path dir) throws Exception {
        Path jar = Files.writeString(dir.resolve("jagt.jar"), "build");

        assertThat(RunningJarWatch.jarFromClassPath(jar.toString(), Files::isRegularFile)).isEqualTo(jar);
    }

    /** Several entries is an exploded IDE/bootRun classpath, which has no single jar anything could watch. */
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
}
