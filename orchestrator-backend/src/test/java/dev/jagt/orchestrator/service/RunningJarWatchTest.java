package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.notify.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

        watch.run();                                            // nothing changed yet
        verifyNoInteractions(notifications);

        Files.writeString(jar, "a different build entirely");
        Files.setLastModifiedTime(jar, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        watch.run();
        watch.run();                                            // the condition persists until a restart

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
    void findsTheJarOnlyWhenTheProcessWasStartedFromExactlyOne(@TempDir Path dir) throws Exception {
        Path jar = Files.writeString(dir.resolve("jagt.jar"), "build");

        assertThat(RunningJarWatch.jarFromClassPath(jar.toString(), Files::isRegularFile)).isEqualTo(jar);
        // A classpath of several entries, or a directory of classes: an exploded/IDE/bootRun run.
        assertThat(RunningJarWatch.jarFromClassPath(jar + java.io.File.pathSeparator + "other.jar",
                Files::isRegularFile)).isNull();
        assertThat(RunningJarWatch.jarFromClassPath(dir.toString(), Files::isRegularFile)).isNull();
        assertThat(RunningJarWatch.jarFromClassPath("does-not-exist.jar", Files::isRegularFile)).isNull();
        assertThat(RunningJarWatch.jarFromClassPath(null, Files::isRegularFile)).isNull();
    }

    @Test
    void comparesBothFactsBecauseARebuildCanKeepEitherOne() {
        var before = new RunningJarWatch.Stamp(1_000, 42);

        assertThat(RunningJarWatch.rewritten(before, new RunningJarWatch.Stamp(1_000, 42))).isFalse();
        assertThat(RunningJarWatch.rewritten(before, new RunningJarWatch.Stamp(2_000, 42))).isTrue();
        assertThat(RunningJarWatch.rewritten(before, new RunningJarWatch.Stamp(1_000, 43))).isTrue();
        assertThat(RunningJarWatch.rewritten(before, null)).isTrue();
    }
}
