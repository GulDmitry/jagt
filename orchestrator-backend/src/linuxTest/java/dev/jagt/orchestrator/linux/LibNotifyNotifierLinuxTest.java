package dev.jagt.orchestrator.linux;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.linux.LibNotifyNotifier;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class LibNotifyNotifierLinuxTest {

    @Test
    void deliversTheNotificationToTheSessionBusWithJagtAsTheApplication() throws Exception {
        Path capture = Files.createTempFile("jagt-bus", ".txt");
        Process monitor = new ProcessBuilder("dbus-monitor", "--session",
                "interface=org.freedesktop.Notifications,member=Notify")
                .redirectOutput(capture.toFile())
                .redirectErrorStream(true)
                .start();
        try {
            awaitCapture(capture, "NameAcquired");
            new LibNotifyNotifier(new ProcessRunner(), "notify-send")
                    .notify("jagt · ABC-1", "your move: read the diff", null);

            assertThat(awaitCapture(capture, "org.freedesktop.Notifications"))
                    .as("the Notify call as the desktop received it")
                    .contains("member=Notify")
                    .contains("\"jagt\"")
                    .contains("\"jagt · ABC-1\"")
                    .contains("\"your move: read the diff\"")
                    .contains("urgency");
        } finally {
            monitor.destroy();
            Files.deleteIfExists(capture);
        }
    }

    @Test
    void sendsATitleThatLooksLikeAnOptionInsteadOfSwallowingIt() throws Exception {
        Path capture = Files.createTempFile("jagt-bus", ".txt");
        Process monitor = new ProcessBuilder("dbus-monitor", "--session",
                "interface=org.freedesktop.Notifications,member=Notify")
                .redirectOutput(capture.toFile())
                .redirectErrorStream(true)
                .start();
        try {
            awaitCapture(capture, "NameAcquired");
            new LibNotifyNotifier(new ProcessRunner(), "notify-send")
                    .notify("--urgency=critical looking title", "body", null);

            assertThat(awaitCapture(capture, "looking title"))
                    .contains("\"--urgency=critical looking title\"");
        } finally {
            monitor.destroy();
            Files.deleteIfExists(capture);
        }
    }

    private static String awaitCapture(Path capture, String expected) {
        return await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(250))
                .until(() -> Files.exists(capture) ? Files.readString(capture) : "", seen -> seen.contains(expected));
    }
}
