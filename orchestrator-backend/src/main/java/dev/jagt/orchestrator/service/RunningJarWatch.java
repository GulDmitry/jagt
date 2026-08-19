package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.notify.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import lombok.extern.slf4j.Slf4j;
import dev.jagt.orchestrator.job.Job;
import org.springframework.stereotype.Service;

import java.time.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Notices that the jar this JVM is running from has been REWRITTEN underneath it, and says so.
 *
 * <p>A rebuild replaces {@code jagt.jar} at the same inode, so already-loaded classes keep working and the next
 * first-time load dies with {@code NoClassDefFoundError} — which surfaces as a board that renders while some
 * endpoints answer 500, and reads exactly like a bug in those endpoints.
 *
 * <p>It only REPORTS: the fix is a restart, and whether to restart while agents are working is the human's call.
 */
@Service
@Slf4j
public class RunningJarWatch implements Job {
    @Override
    public String id() {
        return "jarwatch";
    }

    @Override
    public String describe() {
        return "notice when the jar this process runs from was rebuilt underneath it";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(1);
    }


    /** What a jar file looked like: the two cheap facts that a rewrite cannot leave both unchanged. */
    record Stamp(long lastModified, long size) {
    }

    private final Notifications notifications;
    /** Null when this JVM is not running from a jar at all (an IDE, a test, `bootRun`) — then there is nothing to watch. */
    private final Path jar;
    private final Stamp atStartup;
    private boolean reported;

    // @Autowired disambiguates: the second constructor exists so a test can point the watch at a file it
    // controls, and with two of them Spring otherwise refuses to choose (same reason AutoReviewScheduler has it).
    @org.springframework.beans.factory.annotation.Autowired
    public RunningJarWatch(Notifications notifications) {
        this(notifications, ownJar());
    }

    RunningJarWatch(Notifications notifications, Path jar) {
        this.notifications = notifications;
        this.jar = jar;
        this.atStartup = stamp(jar);
    }

    @Override
    public void run() {
        if (jar == null || atStartup == null || reported) {
            return;
        }
        Stamp now = stamp(jar);
        if (!rewritten(atStartup, now)) {
            return;
        }
        reported = true;                       // once: the condition does not go away until a restart
        log.warn("The jar this jagt is running from was rewritten underneath it ({}). Classes not yet loaded"
                + " will fail with NoClassDefFoundError — /status and /stats typically go 500 first."
                + " Restart from a copy the build does not touch: ./gradlew stageJar && java -jar"
                + " build/libs/jagt-run.jar. Agents keep running in tmux.", jar);
        notifications.send(Notification.install("restart needed",
                "the running jar was rebuilt — parts of the board will fail until you restart"));
    }

    /**
     * A rewrite, not a read: either fact changing is enough, and a file that VANISHED (a {@code clean}) counts
     * too. Pure so the decision is testable without rebuilding anything.
     */
    static boolean rewritten(Stamp atStartup, Stamp now) {
        if (atStartup == null) {
            return false;
        }
        return now == null || now.lastModified() != atStartup.lastModified() || now.size() != atStartup.size();
    }

    private static Stamp stamp(Path jar) {
        if (jar == null) {
            return null;
        }
        try {
            return new Stamp(Files.getLastModifiedTime(jar).toMillis(), Files.size(jar));
        } catch (IOException e) {
            return null;
        }
    }

    /** The jar this JVM was started from, or null when it was not started from one. */
    private static Path ownJar() {
        return jarFromClassPath(System.getProperty("java.class.path"), Files::isRegularFile);
    }

    /**
     * {@code java -jar x.jar} puts EXACTLY that jar on the classpath, which is the one place the path is
     * unambiguous. The obvious alternative — {@code getProtectionDomain().getCodeSource()} — was tried first and
     * silently returned nothing: inside a Spring Boot fat jar the location is a {@code jar:nested:…} URL that
     * {@code Path.of} refuses, so the watch existed and watched nothing (found by rebuilding under a real run,
     * not by a test). Anything else on the classpath — an exploded run, an IDE, {@code bootRun} — means several
     * entries or a directory, and then there is no single jar to watch, which is the honest answer.
     */
    static Path jarFromClassPath(String classPath, java.util.function.Predicate<Path> isFile) {
        if (classPath == null || classPath.isBlank() || classPath.contains(java.io.File.pathSeparator)) {
            return null;
        }
        Path path = Path.of(classPath.strip()).toAbsolutePath().normalize();
        return path.getFileName().toString().endsWith(".jar") && isFile.test(path) ? path : null;
    }
}
