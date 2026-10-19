package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A rebuild replaces {@code jagt.jar} at the same inode, so already-loaded classes keep working and the next
 * first-time load dies with {@code NoClassDefFoundError}, which reads like a bug in the endpoints answering 500.
 * It only REPORTS; the fix is a restart.
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

    private boolean warnedAboutTheBuildsOwnJar;

    /**
     * A build in this tree will rewrite THIS file, and the process then dies on whatever class it had not loaded
     * yet — most visibly on the way out, the first exception-carrying log line needing a logback class.
     */
    private void warnOnceAboutTheBuildsOwnJar() {
        if (warnedAboutTheBuildsOwnJar || !runningTheBuildsOwnJar()) {
            return;
        }
        warnedAboutTheBuildsOwnJar = true;
        log.atWarn().setMessage("running jar unstaged")
                .addKeyValue("jar", jar)
                .addKeyValue("effect", "next ./gradlew build corrupts this process")
                .addKeyValue("fix", "./gradlew stageJar && java -jar build/libs/jagt-run.jar")
                .log();
    }

    /** What a jar file looked like: the two cheap facts that a rewrite cannot leave both unchanged. */
    record Stamp(long lastModified, long size) {
    }

    private final Notifications notifications;
    /** Null when this JVM is not running from a jar at all, and there is nothing to watch. */
    private final @Nullable Path jar;
    private final @Nullable Stamp atStartup;
    private boolean reported;

    public RunningJarWatch(Notifications notifications, Host host) {
        this.notifications = notifications;
        this.jar = host.jar().orElse(null);
        this.atStartup = stamp(jar);
    }

    /** The name {@code bootJar} writes, and the one a build in this tree overwrites in place. */
    private static final String BUILD_OUTPUT = "jagt.jar";

    /** Whether this process is running the jar the build rewrites, rather than a staged copy of it. */
    boolean runningTheBuildsOwnJar() {
        return jar != null && BUILD_OUTPUT.equals(jar.getFileName().toString());
    }

    @Override
    public void run() {
        warnOnceAboutTheBuildsOwnJar();
        if (jar == null || atStartup == null || reported) {
            return;
        }
        Stamp now = stamp(jar);
        if (!rewritten(atStartup, now)) {
            return;
        }
        reported = true;                       // once: the condition does not go away until a restart
        log.atError().setMessage("running jar rewritten")
                .addKeyValue("jar", jar)
                .addKeyValue("effect", "class loading corrupt")
                .addKeyValue("fix", "./gradlew stageJar && java -jar build/libs/jagt-run.jar")
                .log();
        notifications.send(Notification.install("restart needed",
                "the running jar was rebuilt — parts of the board will fail until you restart"));
    }

    /** A rewrite, not a read: either fact changing is enough, and a file that VANISHED counts too. */
    static boolean rewritten(@Nullable Stamp atStartup, @Nullable Stamp now) {
        if (atStartup == null) {
            return false;
        }
        return now == null || now.lastModified() != atStartup.lastModified() || now.size() != atStartup.size();
    }

    private static @Nullable Stamp stamp(@Nullable Path jar) {
        if (jar == null) {
            return null;
        }
        try {
            return new Stamp(Files.getLastModifiedTime(jar).toMillis(), Files.size(jar));
        } catch (IOException e) {
            return null;
        }
    }
}
